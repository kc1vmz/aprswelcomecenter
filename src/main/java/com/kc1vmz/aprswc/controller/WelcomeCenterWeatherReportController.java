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

import com.kc1vmz.aprswc.accessor.WelcomeCenterWeatherReportAccessor;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherReport;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherSummary;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
public class WelcomeCenterWeatherReportController {
    @Autowired
    private WelcomeCenterWeatherReportAccessor accessor;

    @GetMapping("/welcome-center-weather-reports")
    public Flux<WelcomeCenterWeatherReport> all(@RequestParam(required = false) String callsign) {
        return callsign == null ? accessor.findAll() : accessor.findByCallsign(callsign);
    }

    @GetMapping("/welcome-center-weather-reports/{id}")
    public Mono<WelcomeCenterWeatherReport> one(@PathVariable UUID id) {
        return accessor.findById(id);
    }

    @GetMapping("/welcome-centers/{welcomeCenterId}/weather-reports")
    public Flux<WelcomeCenterWeatherReport> byWelcomeCenter(@PathVariable UUID welcomeCenterId) {
        return accessor.findByWelcomeCenterId(welcomeCenterId);
    }

    @GetMapping(value = "/welcome-centers/{welcomeCenterId}/weather-reports.csv", produces = "text/csv")
    public Mono<ResponseEntity<String>> downloadByWelcomeCenter(@PathVariable UUID welcomeCenterId) {
        return accessor.exportCsv(welcomeCenterId).map(csv -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("welcome-center-weather-reports-" + welcomeCenterId + ".csv")
                                .build()
                                .toString())
                .body(csv));
    }

    @GetMapping("/welcome-centers/{welcomeCenterId}/weather-summary")
    public Mono<WelcomeCenterWeatherSummary> summary(@PathVariable UUID welcomeCenterId) {
        return accessor.findSummary(welcomeCenterId);
    }

    @PostMapping("/welcome-center-weather-reports")
    public Mono<ResponseEntity<WelcomeCenterWeatherReport>> create(@RequestBody WelcomeCenterWeatherReport report) {
        return accessor.create(report)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @DeleteMapping("/welcome-center-weather-reports/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return accessor.delete(id).thenReturn(ResponseEntity.noContent().build());
    }

    @DeleteMapping(value = "/welcome-center-weather-reports", params = "callsign")
    public Mono<ResponseEntity<Void>> deleteByCallsign(@RequestParam String callsign) {
        return accessor.deleteByCallsign(callsign)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
