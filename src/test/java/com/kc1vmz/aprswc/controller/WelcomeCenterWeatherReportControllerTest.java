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

import com.kc1vmz.aprswc.accessor.WelcomeCenterWeatherReportAccessor;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterWeatherReportRepository;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherReport;
import java.time.LocalDateTime;
import java.util.List;
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
class WelcomeCenterWeatherReportControllerTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private WelcomeCenterRepository welcomeCenters;

    @Autowired
    private WelcomeCenterWeatherReportRepository reports;

    @Autowired
    private WelcomeCenterWeatherReportAccessor accessor;

    @BeforeEach
    void clearRecords() {
        reports.deleteAll();
        welcomeCenters.deleteAll();
    }

    @Test
    void createsListsReadsFiltersAndDeletesReports() {
        WelcomeCenter center = welcomeCenters.save(new WelcomeCenter(
                null,
                "Center",
                "Description",
                "KC1VMZ",
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
                List.of()));
        LocalDateTime reportTime = LocalDateTime.of(2026, 8, 22, 11, 45);
        WelcomeCenterWeatherReport first = new WelcomeCenterWeatherReport(
                null, "CHANGEME", "07106.00W", "4218.00N", 70.5f, 55.0f, 30.1f, 900.0f, reportTime);
        WelcomeCenterWeatherReport second = new WelcomeCenterWeatherReport(
                null, "CHANGEME", "07112.00W", "4224.00N", 71.0f, 54.0f, 30.0f, 875.0f, reportTime);

        client.post()
                .uri("/api/v1/welcome-center-weather-reports")
                .bodyValue(first)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isNotEmpty()
                .jsonPath("$.callsign")
                .isEqualTo("CHANGEME")
                .jsonPath("$.longitude")
                .isEqualTo("07106.00W")
                .jsonPath("$.latitude")
                .isEqualTo("4218.00N")
                .jsonPath("$.temperature")
                .isEqualTo(70.5);
        client.post()
                .uri("/api/v1/welcome-center-weather-reports")
                .bodyValue(second)
                .exchange()
                .expectStatus()
                .isCreated();

        assertEquals(2, reports.count());
        WelcomeCenterWeatherReport persistedFirst = reports.findAll().stream()
                .filter(report -> Float.valueOf(70.5f).equals(report.getTemperature()))
                .findFirst()
                .orElseThrow();
        assertEquals("07106.00W", persistedFirst.getLongitude());
        assertEquals("4218.00N", persistedFirst.getLatitude());
        assertEquals("CHANGEME", persistedFirst.getCallsign());
        UUID reportId = reports.findAll().getFirst().getId();

        client.get()
                .uri("/api/v1/welcome-center-weather-reports")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(WelcomeCenterWeatherReport.class)
                .hasSize(2);
        client.get()
                .uri("/api/v1/welcome-center-weather-reports/{id}", reportId)
                .exchange()
                .expectStatus()
                .isOk();
        client.get()
                .uri("/api/v1/welcome-centers/{id}/weather-reports", center.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(WelcomeCenterWeatherReport.class)
                .hasSize(0);
        client.put()
                .uri("/api/v1/welcome-center-weather-reports/{id}", reportId)
                .bodyValue(first)
                .exchange()
                .expectStatus()
                .isEqualTo(405);

        client.delete()
                .uri("/api/v1/welcome-center-weather-reports/{id}", reportId)
                .exchange()
                .expectStatus()
                .isNoContent();
        assertEquals(1, reports.count());
    }

    @Test
    void rejectsMissingReportsAndWelcomeCenters() {
        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        WelcomeCenterWeatherReport report = new WelcomeCenterWeatherReport(
                null, "CHANGEME", null, null, null, null, null, null, LocalDateTime.now());

        client.get()
                .uri("/api/v1/welcome-center-weather-reports/{id}", missingId)
                .exchange()
                .expectStatus()
                .isNotFound();
        client.post()
                .uri("/api/v1/welcome-center-weather-reports")
                .bodyValue(report)
                .exchange()
                .expectStatus()
                .isCreated();
        client.get()
                .uri("/api/v1/welcome-centers/{id}/weather-reports", missingId)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void deletesOnlyReportsOlderThanCutoff() {
        LocalDateTime now = LocalDateTime.now();
        WelcomeCenterWeatherReport expired =
                new WelcomeCenterWeatherReport(null, "CHANGEME", null, null, null, null, null, null, now.minusHours(2));
        WelcomeCenterWeatherReport recent = new WelcomeCenterWeatherReport(
                null, "CHANGEME", null, null, null, null, null, null, now.minusMinutes(30));
        WelcomeCenterWeatherReport undated =
                new WelcomeCenterWeatherReport(null, "CHANGEME", null, null, null, null, null, null, null);
        reports.saveAll(List.of(expired, recent, undated));

        Long deleted = accessor.deleteOlderThan(now.minusHours(1)).block();

        assertEquals(1L, deleted);
        assertEquals(2, reports.count());
    }

    @Test
    void listsAndDeletesReportsByStationCallsign() {
        LocalDateTime now = LocalDateTime.now();
        reports.saveAll(List.of(
                new WelcomeCenterWeatherReport(null, "N1ABC", null, null, 70.0f, null, null, null, now),
                new WelcomeCenterWeatherReport(null, "n1abc", null, null, 71.0f, null, null, null, now.minusMinutes(1)),
                new WelcomeCenterWeatherReport(null, "N2ABC", null, null, 72.0f, null, null, null, now)));

        client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/welcome-center-weather-reports")
                        .queryParam("callsign", "N1ABC")
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(WelcomeCenterWeatherReport.class)
                .hasSize(2);

        client.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/welcome-center-weather-reports")
                        .queryParam("callsign", "N1ABC")
                        .build())
                .exchange()
                .expectStatus()
                .isNoContent();

        assertEquals(1, reports.count());
        assertEquals("N2ABC", reports.findAll().getFirst().getCallsign());
    }

    @Test
    void exposesWeatherSummaryForWelcomeCenterWithoutReports() {
        WelcomeCenter center = welcomeCenters.save(new WelcomeCenter(
                null,
                "Center",
                "Description",
                "KC1VMZ",
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
                List.of()));

        client.get()
                .uri("/api/v1/welcome-centers/{id}/weather-summary", center.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.welcomeCenterId")
                .isEqualTo(center.getId().toString());
    }

    @Test
    void downloadsWelcomeCenterWeatherReportsAsCsv() {
        WelcomeCenter center = welcomeCenters.save(new WelcomeCenter(
                null,
                "Center",
                "Description",
                "KC1VMZ",
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
                List.of()));

        client.get()
                .uri("/api/v1/welcome-centers/{id}/weather-reports.csv", center.getId())
                .accept(MediaType.parseMediaType("text/csv"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith("text/csv")
                .expectHeader()
                .valueEquals(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"welcome-center-weather-reports-" + center.getId() + ".csv\"")
                .expectBody(String.class)
                .isEqualTo(
                        "reportTime,callsign,longitude,latitude,temperature,humidity,barometricPressure,luminosity\r\n");
    }
}
