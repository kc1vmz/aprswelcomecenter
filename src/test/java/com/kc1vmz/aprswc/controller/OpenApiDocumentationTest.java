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
package com.kc1vmz.aprswc.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OpenApiDocumentationTest {
    private final WebTestClient client;

    @Autowired
    OpenApiDocumentationTest(WebTestClient client) {
        this.client = client;
    }

    @Test
    void specificationDocumentsEndpointsAndUsesApplicationVersion(
            @Value("${aprswc.application-version.version}") String version) {
        JsonNode document = client.mutate()
                .responseTimeout(Duration.ofSeconds(30))
                .build()
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(document.path("openapi").asText()).startsWith("3.");
        assertThat(document.path("info").path("version").asText()).isEqualTo(version);
        assertThat(document.path("paths").has("/api/v1/welcome-centers")).isTrue();
        assertThat(document.path("paths")
                        .path("/api/v1/station-messages/standalone")
                        .has("post"))
                .isTrue();
        assertThat(document.path("components").path("schemas").has("StationMessage"))
                .isTrue();
        document.path("paths").fieldNames().forEachRemaining(path -> assertThat(path)
                .startsWith("/api/v1/"));
    }

    @Test
    void swaggerUiAndItsConfigurationAreAvailable() {
        String location = client.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst("Location");
        assertThat(location).isNotBlank();
        client.get()
                .uri(location)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("swagger-ui"));
        client.get()
                .uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.url")
                .isEqualTo("/v3/api-docs");
        client.get()
                .uri("/v3/api-docs.yaml")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("openapi:", "/api/v1/welcome-centers:"));
    }
}
