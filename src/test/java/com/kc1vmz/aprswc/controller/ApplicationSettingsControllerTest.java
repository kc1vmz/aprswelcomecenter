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
        ApplicationSettings settings = new ApplicationSettings(null, "https://tiles.example.test/{z}/{x}/{y}.png");
        settings.setPacketRetentionDays(14);

        client.post()
                .uri("/api/v1/application-settings")
                .bodyValue(settings)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isNotEmpty()
                .jsonPath("$.packetRetentionDays")
                .isEqualTo(14)
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
    void validatesAllRetentionSettingsOnCreateAndUpdate() {
        var saved = repository.saveAndFlush(new ApplicationSettings(null, null));
        for (String field : new String[] {"packetRetentionDays", "stationRetentionDays", "messageRetentionDays"}) {
            for (String value : new String[] {"0", "-1", "null"}) {
                String body = "{\"" + field + "\":" + value + "}";
                client.post()
                        .uri("/api/v1/application-settings")
                        .header("Content-Type", "application/json")
                        .bodyValue(body)
                        .exchange()
                        .expectStatus()
                        .isBadRequest();
                client.put()
                        .uri("/api/v1/application-settings/" + saved.getId())
                        .header("Content-Type", "application/json")
                        .bodyValue(body)
                        .exchange()
                        .expectStatus()
                        .isBadRequest();
            }
        }
        client.put()
                .uri("/api/v1/application-settings/" + saved.getId())
                .header("Content-Type", "application/json")
                .bodyValue("{\"packetRetentionDays\":7,\"stationRetentionDays\":14,\"messageRetentionDays\":21}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.packetRetentionDays")
                .isEqualTo(7)
                .jsonPath("$.stationRetentionDays")
                .isEqualTo(14)
                .jsonPath("$.messageRetentionDays")
                .isEqualTo(21);
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
