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

import com.kc1vmz.aprswc.accessor.StationAccessor;
import com.kc1vmz.aprswc.database.IgnoreStationRepository;
import com.kc1vmz.aprswc.database.StationMessageRepository;
import com.kc1vmz.aprswc.database.StationPacketRepository;
import com.kc1vmz.aprswc.database.StationPositionRepository;
import com.kc1vmz.aprswc.database.StationRepository;
import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.IgnoreStation;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.StationPosition;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class StationControllerTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private StationRepository stations;

    @Autowired
    private IgnoreStationRepository ignoredStations;

    @Autowired
    private StationPositionRepository positions;

    @Autowired
    private StationPacketRepository packets;

    @Autowired
    private StationMessageRepository messages;

    @Autowired
    private StationAccessor accessor;

    @BeforeEach
    void clearData() {
        positions.deleteAll();
        stations.deleteAll();
        ignoredStations.deleteAll();
        packets.deleteAll();
        messages.deleteAll();
    }

    @Test
    void excludesIgnoredStationsWhenRequested() {
        stations.save(new Station(null, "N1ABC", StationState.STATIONARY, List.of()));
        stations.save(new Station(null, "KC1VMZ", StationState.MOVING, List.of()));
        ignoredStations.save(new IgnoreStation(null, "n1abc"));

        client.get()
                .uri("/api/v1/stations?excludeIgnored=true")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].callsign")
                .isEqualTo("KC1VMZ")
                .jsonPath("$[1]")
                .doesNotExist();
    }

    @Test
    void includesIgnoredStationsWhenRequested() {
        stations.save(new Station(null, "N1ABC", StationState.UNKNOWN, List.of()));
        ignoredStations.save(new IgnoreStation(null, "N1ABC"));

        client.get()
                .uri("/api/v1/stations?excludeIgnored=false")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].callsign")
                .isEqualTo("N1ABC");
    }

    @Test
    void limitsPositionsAndOrdersNewestFirst() {
        Station station = new Station(null, "N1ABC", StationState.UNKNOWN, List.of());
        LocalDateTime firstTime = LocalDateTime.of(2026, 8, 22, 10, 0);
        for (int index = 0; index < 7; index++) {
            station.addPosition(
                    new StationPosition(null, "NOCALL-1", "07106.00W", "4218.00N", firstTime.plusMinutes(index)));
        }
        station = stations.saveAndFlush(station);

        List<StationPosition> positions =
                accessor.findPositions(station.getId(), 5).collectList().block();

        assertEquals(5, positions.size());
        assertEquals(firstTime.plusMinutes(6), positions.get(0).getCreatedTime());
        assertEquals(firstTime.plusMinutes(2), positions.get(4).getCreatedTime());
    }

    @Test
    void returnsAllPositionsNewestFirst() {
        Station station = new Station(null, "N1ABC", StationState.UNKNOWN, List.of());
        LocalDateTime firstTime = LocalDateTime.of(2026, 8, 22, 10, 0);
        for (int index = 0; index < 3; index++) {
            station.addPosition(
                    new StationPosition(null, "NOCALL-1", "07106.00W", "4218.00N", firstTime.plusMinutes(index)));
        }
        station = stations.saveAndFlush(station);

        List<StationPosition> positions =
                accessor.findAllPositions(station.getId()).collectList().block();

        assertEquals(3, positions.size());
        assertEquals(firstTime.plusMinutes(2), positions.get(0).getCreatedTime());
        assertEquals(firstTime, positions.get(2).getCreatedTime());
    }

    @Test
    void addsMultiplePositionsToOneStation() {
        Station station = stations.saveAndFlush(new Station(null, "N1ABC", StationState.MOVING, List.of()));
        LocalDateTime firstTime = LocalDateTime.of(2026, 8, 22, 10, 0);

        accessor.addPosition(station.getId(), new StationPosition(null, "NOCALL-1", "07106.00W", "4218.00N", firstTime))
                .block();
        accessor.addPosition(
                        station.getId(),
                        new StationPosition(null, "NOCALL-1", "07106.01W", "4218.01N", firstTime.plusMinutes(1)))
                .block();

        List<StationPosition> positions =
                accessor.findAllPositions(station.getId()).collectList().block();
        assertEquals(2, positions.size());
        assertEquals("NOCALL-1", positions.get(0).getCallsign());
        assertEquals(station.getId(), positions.get(0).getStationId());
        assertEquals(station.getId(), positions.get(1).getStationId());
    }

    @Test
    void deletesAllPositionsWithoutDeletingStation() {
        Station station = new Station(null, "N1POS", StationState.UNKNOWN, List.of());
        station.addPosition(
                new StationPosition(null, "N1POS", "07106.00W", "4218.00N", LocalDateTime.of(2026, 8, 24, 12, 0)));
        station.addPosition(
                new StationPosition(null, "N1POS", "07106.01W", "4218.01N", LocalDateTime.of(2026, 8, 24, 12, 1)));
        station = stations.saveAndFlush(station);

        client.delete()
                .uri("/api/v1/stations/{id}/positions", station.getId())
                .exchange()
                .expectStatus()
                .isNoContent();

        assertEquals(0, positions.count());
        assertEquals(1, stations.count());
    }

    @Test
    void deletesStationPositionsAndPacketsForStationCallsign() {
        Station station = new Station(null, "N1DEL", StationState.UNKNOWN, List.of());
        station.addPosition(
                new StationPosition(null, "N1DEL", "07106.00W", "4218.00N", LocalDateTime.of(2026, 8, 24, 12, 0)));
        station = stations.saveAndFlush(station);
        packets.save(
                new StationPacket(null, "processor", "n1del", LocalDateTime.of(2026, 8, 24, 12, 0), "related", null));
        packets.save(new StationPacket(
                null, "processor", "N2KEEP", LocalDateTime.of(2026, 8, 24, 12, 0), "unrelated", null));
        messages.save(new StationMessage(
                null,
                "n1del",
                "KC1VMZ",
                null,
                null,
                "related",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));
        messages.save(new StationMessage(
                null,
                "N2KEEP",
                "KC1VMZ",
                null,
                null,
                "unrelated",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));

        client.delete()
                .uri("/api/v1/stations/{id}", station.getId())
                .exchange()
                .expectStatus()
                .isNoContent();

        org.assertj.core.api.Assertions.assertThat(stations.findById(station.getId()))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(positions.findAll()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(packets.findAll())
                .extracting(StationPacket::getCallsign)
                .containsExactly("N2KEEP");
        org.assertj.core.api.Assertions.assertThat(messages.findAll())
                .extracting(StationMessage::getCallsignTo)
                .containsExactly("N2KEEP");
    }

    @Test
    void ignoresStationOnlyOnce() {
        Station station = stations.saveAndFlush(new Station(null, "N1IGN", StationState.UNKNOWN, List.of()));

        client.post()
                .uri("/api/v1/stations/{id}/ignore", station.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.callsign")
                .isEqualTo("N1IGN");
        client.post()
                .uri("/api/v1/stations/{id}/ignore", station.getId())
                .exchange()
                .expectStatus()
                .isOk();

        org.assertj.core.api.Assertions.assertThat(ignoredStations.findAll())
                .extracting(IgnoreStation::getCallsign)
                .containsExactly("N1IGN");
    }

    @Test
    void deletesAllStationsPositionsAndPacketsButKeepsIgnoredStations() {
        Station first = new Station(null, "N1ONE", StationState.UNKNOWN, List.of());
        first.addPosition(
                new StationPosition(null, "N1ONE", "07106.00W", "4218.00N", LocalDateTime.of(2026, 8, 24, 12, 0)));
        stations.saveAndFlush(first);
        stations.saveAndFlush(new Station(null, "N2TWO", StationState.UNKNOWN, List.of()));
        packets.save(
                new StationPacket(null, "processor", "N1ONE", LocalDateTime.of(2026, 8, 24, 12, 0), "packet", null));
        packets.save(new StationPacket(
                null, "processor", "N3ORPHAN", LocalDateTime.of(2026, 8, 24, 12, 0), "unrelated", null));
        ignoredStations.save(new IgnoreStation(null, "N3IGNORE"));

        client.delete().uri("/api/v1/stations").exchange().expectStatus().isNoContent();

        assertEquals(0, stations.count());
        assertEquals(0, positions.count());
        org.assertj.core.api.Assertions.assertThat(packets.findAll())
                .extracting(StationPacket::getCallsign)
                .containsExactly("N3ORPHAN");
        assertEquals(1, ignoredStations.count());
    }

    @Test
    void listsLastHeardFromLatestPacketRegardlessOfCallsignCase() {
        stations.saveAndFlush(new Station(null, "N1HEARD", StationState.UNKNOWN, List.of()));
        stations.saveAndFlush(new Station(null, "N1QUIET", StationState.UNKNOWN, List.of()));
        LocalDateTime latest = LocalDateTime.of(2026, 9, 18, 12, 0);
        packets.saveAndFlush(new StationPacket(null, "test", "n1heard", latest, "latest", null));
        packets.saveAndFlush(new StationPacket(null, "test", "N1HEARD", latest.minusHours(4), "older", null));
        client.get()
                .uri("/api/v1/stations")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(Station.class)
                .value(rows -> {
                    Station heard = rows.stream()
                            .filter(row -> row.getCallsign().equals("N1HEARD"))
                            .findFirst()
                            .orElseThrow();
                    Station quiet = rows.stream()
                            .filter(row -> row.getCallsign().equals("N1QUIET"))
                            .findFirst()
                            .orElseThrow();
                    assertEquals(latest.atZone(java.time.ZoneId.systemDefault()).toInstant(), heard.getLastHeard());
                    org.assertj.core.api.Assertions.assertThat(quiet.getLastHeard())
                            .isNull();
                });
    }
}
