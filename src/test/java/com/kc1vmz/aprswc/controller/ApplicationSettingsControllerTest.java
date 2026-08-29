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

import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApplicationSettingsControllerTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private ApplicationSettingsRepository repository;

    @BeforeEach
    void clearSettings() {
        repository.deleteAll();
    }

    @Test
    void createsAndReadsSettings() {
        ApplicationSettings settings = new ApplicationSettings(
                null,
                true,
                true,
                "server",
                "user",
                "passcode",
                "14580",
                "host",
                "8001",
                "9600",
                "KISS ON",
                null,
                "WIDE1-1",
                "m/25",
                "https://tiles.example.test/{z}/{x}/{y}.png");

        client.post()
                .uri("/api/v1/application-settings")
                .bodyValue(settings)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isNotEmpty()
                .jsonPath("$.usingInternetServer")
                .isEqualTo(true)
                .jsonPath("$.kissInitCommand1")
                .isEqualTo("KISS ON")
                .jsonPath("$.kissInitCommand2")
                .doesNotExist()
                .jsonPath("$.filter")
                .isEqualTo("m/25")
                .jsonPath("$.mapTileUrl")
                .isEqualTo("https://tiles.example.test/{z}/{x}/{y}.png");

        client.get()
                .uri("/api/v1/application-settings")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(ApplicationSettings.class)
                .hasSize(1);
    }

    @Test
    void rejectsMissingSettings() {
        client.get()
                .uri("/api/v1/application-settings/00000000-0000-0000-0000-000000000000")
                .exchange()
                .expectStatus()
                .isNotFound();
    }
}
