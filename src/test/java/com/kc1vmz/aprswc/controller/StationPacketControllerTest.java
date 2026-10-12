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
    void stationHistoryIncludesSourceAndMessageRecipientButExcludesBulletinsAndOtherSsids() {
        String[] packets = {
            "N1ABC-1>APRS:>Station status",
            "CENTER>APRS::N1ABC-1  :Hello{00001",
            "N1ABC-1>APRS::BLN1     :Bulletin",
            "CENTER>APRS::BLN1     :N1ABC-1 event",
            "N1ABC-2>APRS:>Other SSID",
            "CENTER>APRS::N1ABC-2  :Other recipient",
            "OTHER>APRS:>Mentions N1ABC-1"
        };
        for (int i = 0; i < packets.length; i++) {
            repository.saveAndFlush(new StationPacket(
                    null,
                    "route",
                    packets[i].split(">")[0],
                    LocalDateTime.now(),
                    packets[i],
                    null,
                    i == 1 ? "TX" : "RX"));
        }
        client.get()
                .uri("/api/v1/station-packets?callsign=n1abc-1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(StationPacket.class)
                .hasSize(2)
                .value(history -> {
                    assertEquals(
                            1,
                            history.stream()
                                    .filter(packet -> "TX".equals(packet.getDirection()))
                                    .count());
                    assertEquals(
                            1,
                            history.stream()
                                    .filter(packet -> "RX".equals(packet.getDirection()))
                                    .count());
                });
        client.get()
                .uri("/api/v1/station-packets/csv?callsign=N1ABC-1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(csv -> {
                    assertEquals(3, csv.lines().count());
                    assertEquals(false, csv.contains("Bulletin"));
                });
        client.get()
                .uri("/api/v1/station-packets")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(StationPacket.class)
                .hasSize(packets.length);
    }

    @Test
    void deletesAllPackets() {
        repository.save(
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "first", null));
        repository.save(new StationPacket(
                UUID.randomUUID(), "processor-1", "N2ABC", LocalDateTime.now(), "second", null, "TX"));

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
        LocalDateTime newest = LocalDateTime.now().withNano(0);
        LocalDateTime oldest = newest.minusHours(1);
        repository.save(
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", oldest, "first, \"quoted\"", null));
        repository.save(new StationPacket(UUID.randomUUID(), "processor-2", "N2ABC", newest, "second", null, "TX"));

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
                .isEqualTo("time,direction,communicationInstanceId,callsign,command\r\n"
                        + "\"" + newest + "\",\"TX\",\"processor-2\",\"N2ABC\",\"second\"\r\n"
                        + "\"" + oldest + "\",\"RX\",\"processor-1\",\"N1ABC\",\"first, \"\"quoted\"\"\"\r\n");
    }
}
