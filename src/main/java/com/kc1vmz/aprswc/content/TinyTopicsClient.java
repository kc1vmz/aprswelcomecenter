/*
 *
 * APRSWelcomeCenter
 * Copyright (c) 2026 John Rokicki KC1VMZ
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * https://www.gnu.org/licenses/.
 *
 * http://www.kc1vmz.com
 */
package com.kc1vmz.aprswc.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

@Service
public class TinyTopicsClient {
    public static final String FALLBACK = "TinyTopics content not currently available.";
    private final ApplicationSettingsRepository settings;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public TinyTopicsClient(ApplicationSettingsRepository settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
        this.client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public record Result(String status, Integer httpStatus, String error, String text) {}

    public JsonNode topics() throws IOException, InterruptedException {
        var response = get("/api/v1/tinyTopicDefinition");
        if (response.statusCode() != 200) throw new IOException("TinyTopics returned HTTP " + response.statusCode());
        JsonNode topics = mapper.readTree(response.body());
        if (topics == null || !topics.isArray()) throw new IOException("Malformed TinyTopics topic list");
        for (JsonNode topic : topics) {
            if (!topic.path("id").isTextual() || !topic.path("parameterNames").isArray())
                throw new IOException("Malformed TinyTopics topic definition");
        }
        return topics;
    }

    public Result resolve(String topicId, Map<String, String> parameters) {
        Integer status = null;
        try {
            StringBuilder path = new StringBuilder("/api/v1/tinyTopicResult/")
                    .append(encode(topicId))
                    .append("?length=64&format=APRS");
            parameters.forEach((name, value) -> {
                if (!value.isBlank())
                    path.append('&').append(encode(name)).append('=').append(encode(value));
            });
            var response = get(path.toString());
            status = response.statusCode();
            JsonNode body;
            try {
                body = mapper.readTree(response.body());
            } catch (IOException error) {
                return failure(status, "Malformed TinyTopics response (HTTP " + status + ")");
            }
            if (body == null || !body.isObject()) return failure(status, "Empty or malformed TinyTopics response");
            String resultStatus = body.path("status").asText();
            if (status != 200 || "ERROR".equals(resultStatus))
                return failure(
                        status,
                        "HTTP " + status + ": " + body.path("errorMessage").asText("TinyTopics request failed"));
            if ("NO_CONTENT".equals(resultStatus)) return new Result("NO_CONTENT", status, null, FALLBACK);
            if (!"CONTENT".equals(resultStatus)
                    || !body.path("text").isTextual()
                    || body.path("text").asText().isBlank())
                return failure(status, "Malformed TinyTopics CONTENT response or empty text");
            return new Result("CONTENT", status, null, body.path("text").asText());
        } catch (HttpTimeoutException error) {
            return failure(status, "TinyTopics request timed out: " + error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return failure(status, "TinyTopics request interrupted");
        } catch (IOException | IllegalArgumentException error) {
            return failure(
                    status,
                    "TinyTopics request failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private Result failure(Integer status, String error) {
        return new Result("ERROR", status, error, FALLBACK);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        String base = settings.findAll().stream()
                .findFirst()
                .map(ApplicationSettings::getTinyTopicsServerUrl)
                .orElse(ApplicationSettings.DEFAULT_TINY_TOPICS_SERVER_URL);
        URI uri = URI.create(base.replaceAll("/+$", "") + path);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) throw new IOException("TinyTopics server URL must use HTTP or HTTPS");
        // Allow ten seconds to connect and twenty seconds for the complete request.
        var request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            return pending.get(20, TimeUnit.SECONDS);
        } catch (TimeoutException error) {
            pending.cancel(true);
            throw new HttpTimeoutException("No complete response within 20 seconds");
        } catch (InterruptedException error) {
            pending.cancel(true);
            throw error;
        } catch (ExecutionException error) {
            if (error.getCause() instanceof IOException cause) throw cause;
            throw new IOException("TinyTopics request failed", error.getCause());
        }
    }

    @PreDestroy
    public void close() {
        client.shutdownNow();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
