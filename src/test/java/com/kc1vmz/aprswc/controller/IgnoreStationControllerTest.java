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

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kc1vmz.aprswc.database.IgnoreStationRepository;
import com.kc1vmz.aprswc.object.IgnoreStation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class IgnoreStationControllerTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private IgnoreStationRepository repository;

    @BeforeEach
    void clearStations() {
        repository.deleteAll();
    }

    @Test
    void createsAndReadsIgnoreStation() {
        client.post()
                .uri("/api/v1/ignore-stations")
                .bodyValue(new IgnoreStation(null, "KC1VMZ"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isNotEmpty()
                .jsonPath("$.callsign")
                .isEqualTo("KC1VMZ");

        client.get()
                .uri("/api/v1/ignore-stations")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(IgnoreStation.class)
                .hasSize(1);
    }

    @Test
    void rejectsInvalidAndMissingIgnoreStations() {
        client.post()
                .uri("/api/v1/ignore-stations")
                .bodyValue(new IgnoreStation(null, "TOO-LONG-CALLSIGN"))
                .exchange()
                .expectStatus()
                .isBadRequest();

        client.get()
                .uri("/api/v1/ignore-stations/00000000-0000-0000-0000-000000000000")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void databaseRejectsDuplicateCallsigns() {
        repository.saveAndFlush(new IgnoreStation(null, "N1ABC"));
        assertThrows(
                DataIntegrityViolationException.class, () -> repository.saveAndFlush(new IgnoreStation(null, "N1ABC")));
    }
}
