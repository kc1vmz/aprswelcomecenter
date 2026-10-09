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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TinyTopicsClientTest {
    private HttpServer server;
    private TinyTopicsClient client;
    private final AtomicReference<String> uri = new AtomicReference<>();
    private String body;
    private int status;
    private ExecutorService executor;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/api/v1/", exchange -> {
            uri.set(exchange.getRequestURI().toString());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        var settings = mock(ApplicationSettingsRepository.class);
        when(settings.findAll())
                .thenReturn(List.of(new ApplicationSettings(
                        null, null, "http://127.0.0.1:" + server.getAddress().getPort())));
        client = new TinyTopicsClient(settings, new ObjectMapper());
        status = 200;
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
        client.close();
    }

    @Test
    void timesOutAnIncompleteResponseBodyAfterAtLeastTenSeconds() {
        server.createContext("/api/v1/tinyTopicResult/slow", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(30000);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        Instant start = Instant.now();
        var result = client.resolve("slow", Map.of());
        assertThat(Duration.between(start, Instant.now()).toSeconds()).isBetween(10L, 27L);
        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.error()).contains("timed out");
        assertThat(result.text()).isEqualTo(TinyTopicsClient.FALLBACK);
    }

    @Test
    void forwardsEncodedParametersAndRequestsAprsWith64CharacterLimit() {
        body = "{\"status\":\"CONTENT\",\"text\":\"Sunny today\"}";
        var result = client.resolve("forecast", Map.of("x", "-72.97", "label", "a & b", "y", ""));
        assertThat(result.text()).isEqualTo("Sunny today");
        assertThat(result.status()).isEqualTo("CONTENT");
        assertThat(uri.get())
                .startsWith("/api/v1/tinyTopicResult/forecast?length=64&format=APRS")
                .contains("x=-72.97", "label=a+%26+b")
                .doesNotContain("&y=");
    }

    @Test
    void distinguishesNoContentFromHttpAndMalformedErrors() {
        body = "{\"status\":\"NO_CONTENT\"}";
        assertThat(client.resolve("test", Map.of()))
                .isEqualTo(new TinyTopicsClient.Result("NO_CONTENT", 200, null, TinyTopicsClient.FALLBACK));
        for (int code : new int[] {400, 404, 422, 502}) {
            status = code;
            body = "{\"status\":\"ERROR\",\"errorMessage\":\"Topic failed\"}";
            var result = client.resolve("test", Map.of());
            assertThat(result.status()).isEqualTo("ERROR");
            assertThat(result.httpStatus()).isEqualTo(code);
            assertThat(result.error()).contains("Topic failed");
            assertThat(result.text()).isEqualTo(TinyTopicsClient.FALLBACK);
        }
        status = 200;
        for (String malformed : List.of("<html>oops</html>", "{}", "{\"status\":\"CONTENT\",\"text\":\"\"}")) {
            body = malformed;
            assertThat(client.resolve("test", Map.of()).status()).isEqualTo("ERROR");
        }
    }

    @Test
    void listsDefinitionsAndReportsUnreachableServer() throws Exception {
        body = "[{\"id\":\"forecast\",\"parameterNames\":[\"x\",\"y\"]}]";
        assertThat(client.topics().get(0).path("id").asText()).isEqualTo("forecast");
        server.stop(0);
        var result = client.resolve("test", Map.of());
        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.error()).contains("TinyTopics request failed");
        assertThat(result.text()).isEqualTo(TinyTopicsClient.FALLBACK);
    }
}
