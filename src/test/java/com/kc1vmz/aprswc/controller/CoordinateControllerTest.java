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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CoordinateControllerTest {
    @Autowired
    private WebTestClient client;

    @Test
    void postsCoordinatesToConverter() {
        client.post()
                .uri("/api/v1/coordinates")
                .bodyValue(List.of(Map.of("longitude", "07106.00W", "latitude", "4218.00N")))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].valid")
                .isEqualTo(true)
                .jsonPath("$[0].longitude")
                .isEqualTo(-71.1)
                .jsonPath("$[0].latitude")
                .isEqualTo(42.3);
    }

    @Test
    void rejectsUnsupportedGetRequests() {
        client.get().uri("/api/v1/coordinates").exchange().expectStatus().isEqualTo(405);
    }

    @Test
    void postsDecimalCoordinatesForAprsFormatting() {
        client.post()
                .uri("/api/v1/coordinates/aprs")
                .bodyValue(List.of(Map.of("longitude", -71.1, "latitude", 42.3)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].longitude")
                .isEqualTo("07106.00W")
                .jsonPath("$[0].latitude")
                .isEqualTo("4218.00N");
    }

    @Test
    void servesPackagedLeafletAssets() {
        client.get()
                .uri("/webjars/leaflet/1.9.4/leaflet.js")
                .exchange()
                .expectStatus()
                .isOk();
    }
}
