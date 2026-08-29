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

import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.enumeration.DistanceUnit;
import com.kc1vmz.aprswc.enumeration.RegionType;
import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class WelcomeCenterControllerTest {
    @Autowired
    WebTestClient client;

    @Autowired
    WelcomeCenterRepository repository;

    @Autowired
    WelcomeRegionRepository regionRepository;

    @Autowired
    StationRepository stationRepository;

    @Test
    void createReadAndRejectMissing() {
        repository.deleteAll();
        WelcomeCenter value = new WelcomeCenter(
                null, "Center", "Test", "KC1VMZ", null, null, null, null, null, null, null, null, "c", "/", List.of());
        String location = client.post()
                                .uri("/api/v1/welcome-centers")
                                .bodyValue(value)
                                .exchange()
                                .expectStatus()
                                .isCreated()
                                .expectBody()
                                .jsonPath("$.id")
                                .isNotEmpty()
                                .returnResult()
                                .getResponseBodyContent()
                        != null
                ? "ok"
                : "";
        client.get()
                .uri("/api/v1/welcome-centers")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(WelcomeCenter.class)
                .hasSize(1);
        client.get()
                .uri("/api/v1/welcome-centers/00000000-0000-0000-0000-000000000000")
                .exchange()
                .expectStatus()
                .isNotFound();
        assert !location.isEmpty();
    }

    @Test
    void deletesRegionFromWelcomeCenterAndDatabase() {
        repository.deleteAll();
        WelcomeCenter center = new WelcomeCenter(
                null, "Center", "Test", "N1TEST", null, null, null, null, null, null, null, null, "c", "/", List.of());
        WelcomeRegion region = new WelcomeRegion(
                null,
                "Region",
                "Test region",
                RegionType.RECTANGLE,
                "07112.00W",
                "4224.00N",
                "07100.00W",
                "4212.00N",
                null,
                null,
                null,
                DistanceUnit.MILES);
        center.addRegion(region);
        center = repository.saveAndFlush(center);

        client.delete()
                .uri("/api/v1/welcome-centers/{centerId}/regions/{regionId}", center.getId(), region.getId())
                .exchange()
                .expectStatus()
                .isNoContent();

        org.assertj.core.api.Assertions.assertThat(regionRepository.findById(region.getId()))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(
                        repository.findById(center.getId()).orElseThrow().getRegions())
                .isEmpty();
    }

    @Test
    void renamesWelcomeCenterWithoutChangingIdOrRegions() {
        repository.deleteAll();
        WelcomeCenter center = new WelcomeCenter(
                null,
                "Original name",
                "Test",
                "N1KEEP",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "c",
                "/",
                List.of());
        WelcomeRegion region = new WelcomeRegion(
                null,
                "Preserved region",
                "Test region",
                RegionType.CIRCLE,
                null,
                null,
                null,
                null,
                "07106.00W",
                "4218.00N",
                10,
                DistanceUnit.MILES);
        center.addRegion(region);
        center = repository.saveAndFlush(center);
        var centerId = center.getId();
        var regionId = region.getId();
        WelcomeCenter replacement = new WelcomeCenter(
                null,
                "Renamed center",
                "Updated",
                "N1KEEP",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "c",
                "/",
                List.of());

        client.put()
                .uri("/api/v1/welcome-centers/{id}", centerId)
                .bodyValue(replacement)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(centerId.toString())
                .jsonPath("$.name")
                .isEqualTo("Renamed center")
                .jsonPath("$.regions.length()")
                .isEqualTo(1)
                .jsonPath("$.regions[0].id")
                .isEqualTo(regionId.toString());

        WelcomeCenter reloaded = repository.findById(centerId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getName()).isEqualTo("Renamed center");
        org.assertj.core.api.Assertions.assertThat(reloaded.getRegions())
                .extracting(WelcomeRegion::getId)
                .containsExactly(regionId);
        org.assertj.core.api.Assertions.assertThat(regionRepository.findById(regionId))
                .isPresent();
    }

    @Test
    void returnsStationPositionsWithinWelcomeCenterRegions() {
        stationRepository.deleteAll();
        repository.deleteAll();
        WelcomeCenter center = new WelcomeCenter(
                null, "Center", "Test", "N1TEST", null, null, null, null, null, null, null, null, "c", "/", List.of());
        center.addRegion(new WelcomeRegion(
                null,
                "Region",
                "Test region",
                RegionType.RECTANGLE,
                "07112.00W",
                "4224.00N",
                "07100.00W",
                "4212.00N",
                null,
                null,
                null,
                DistanceUnit.MILES));
        center = repository.saveAndFlush(center);
        Station station = new Station(null, "N1ABC", StationState.STATIONARY, List.of());
        station.addPosition(
                new StationPosition(null, "N1ABC", "07106.00W", "4218.00N", LocalDateTime.of(2026, 8, 23, 12, 0)));
        stationRepository.saveAndFlush(station);

        client.get()
                .uri("/api/v1/welcome-centers/{id}/station-positions", center.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].callsign")
                .isEqualTo("N1ABC")
                .jsonPath("$[0].longitude")
                .isEqualTo("07106.00W")
                .jsonPath("$[0].latitude")
                .isEqualTo("4218.00N");
    }
}
