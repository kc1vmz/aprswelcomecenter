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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApplicationVersionControllerTest {
    @Autowired
    private WebTestClient client;

    @Test
    void getsConfiguredApplicationVersionAndRejectsWrites() {
        client.get()
                .uri("/api/v1/application-version")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.applicationName")
                .isEqualTo("APRSWelcomeCenter")
                .jsonPath("$.version")
                .isEqualTo("1.0.4")
                .jsonPath("$.author")
                .isEqualTo("John Rokicki KC1VMZ")
                .jsonPath("$.copyrightYear")
                .isEqualTo("2026")
                .jsonPath("$.website")
                .isEqualTo("http://www.kc1vmz.com");

        client.post()
                .uri("/api/v1/application-version")
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isEqualTo(405);
    }

    @Test
    void servesLicenseResource() {
        client.get()
                .uri("/LICENSE.txt")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(license -> {
                    org.assertj.core.api.Assertions.assertThat(license)
                            .contains("GNU General Public License")
                            .contains("John Rokicki KC1VMZ");
                });
    }
}
