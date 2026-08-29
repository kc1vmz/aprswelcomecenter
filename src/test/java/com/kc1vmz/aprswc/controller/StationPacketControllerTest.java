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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kc1vmz.aprswc.database.StationPacketRepository;
import com.kc1vmz.aprswc.object.StationPacket;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class StationPacketControllerTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private StationPacketRepository repository;

    @BeforeEach
    void clearPackets() {
        repository.deleteAll();
    }

    @Test
    void deletesAllPackets() {
        repository.save(
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "first", null));
        repository.save(
                new StationPacket(UUID.randomUUID(), "processor-1", "N2ABC", LocalDateTime.now(), "second", null));

        client.delete()
                .uri("/api/v1/station-packets")
                .exchange()
                .expectStatus()
                .isNoContent()
                .expectBody()
                .isEmpty();

        assertEquals(0, repository.count());
    }

    @Test
    void persistsPacketHeader() {
        String header = "N1ABC>APRS,WIDE1-1";
        StationPacket saved = repository.saveAndFlush(
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "packet", header));

        StationPacket reloaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals(header, reloaded.getHeader());
    }

    @Test
    void downloadsDisplayedPacketFieldsAsCsvNewestFirst() {
        repository.save(new StationPacket(
                UUID.randomUUID(),
                "processor-1",
                "N1ABC",
                LocalDateTime.of(2026, 8, 24, 10, 30),
                "first, \"quoted\"",
                null));
        repository.save(new StationPacket(
                UUID.randomUUID(), "processor-2", "N2ABC", LocalDateTime.of(2026, 8, 24, 11, 30), "second", null));

        client.get()
                .uri("/api/v1/station-packets/csv")
                .accept(MediaType.parseMediaType("text/csv"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith("text/csv")
                .expectHeader()
                .valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"station-packets.csv\"")
                .expectBody(String.class)
                .isEqualTo("receivedTime,callsign,command\r\n"
                        + "\"2026-08-24T11:30\",\"N2ABC\",\"second\"\r\n"
                        + "\"2026-08-24T10:30\",\"N1ABC\",\"first, \"\"quoted\"\"\"\r\n");
    }
}
