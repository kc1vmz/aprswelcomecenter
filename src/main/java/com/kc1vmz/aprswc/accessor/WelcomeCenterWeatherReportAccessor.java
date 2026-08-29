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
package com.kc1vmz.aprswc.accessor;

import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterWeatherReportRepository;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherReport;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherSummary;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import com.kc1vmz.aprswc.utils.GeoFenceUtils;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class WelcomeCenterWeatherReportAccessor {
    private static final Logger logger = LoggerFactory.getLogger("WelcomeCenterWeatherReportAccessor");

    @Autowired
    private WelcomeCenterWeatherReportRepository reports;

    @Autowired
    private WelcomeCenterRepository welcomeCenters;

    @Autowired
    private GeoFenceUtils geoFenceUtils;

    @Autowired
    private WelcomeCenterAccessor welcomeCenterAccessor;

    public Flux<WelcomeCenterWeatherReport> findAll() {
        return Mono.fromCallable(reports::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<WelcomeCenterWeatherReport> findById(UUID id) {
        return Mono.fromCallable(() -> reports.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Flux<WelcomeCenterWeatherReport> findByCallsign(String callsign) {
        return Mono.fromCallable(() -> reports.findByCallsignIgnoreCaseOrderByReportTimeDesc(callsign))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Flux<WelcomeCenterWeatherReport> findByWelcomeCenterId(UUID welcomeCenterId) {
        return Mono.fromRunnable(() -> requireWelcomeCenter(welcomeCenterId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(findAll())
                .filter(report -> isWeatherReportForWelcomeCenter(report, welcomeCenterId))
                .sort(Comparator.comparing(
                        WelcomeCenterWeatherReport::getReportTime, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    public boolean isWeatherReportForWelcomeCenter(WelcomeCenterWeatherReport report, UUID welcomeCenterId) {
        try {
            WelcomeCenter welcomeCenter =
                    welcomeCenterAccessor.findById(welcomeCenterId).block();
            if (welcomeCenter == null) {
                return false;
            }

            List<WelcomeRegion> welcomeRegions = welcomeCenter.getRegions();
            StationPosition weatherReportPosition =
                    new StationPosition(null, null, report.getLongitude(), report.getLatitude(), null);
            if (welcomeRegions != null) {
                for (WelcomeRegion welcomeRegion : welcomeRegions) {
                    if (geoFenceUtils.isInGeoFenceRegion(welcomeRegion, weatherReportPosition)) {
                        // report is in a welcome center region
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exception caught including weather report for weather center", e);
        }
        return false;
    }

    public Mono<WelcomeCenterWeatherSummary> findSummary(UUID welcomeCenterId) {
        return findByWelcomeCenterId(welcomeCenterId)
                .collectList()
                .map(weatherReports -> new WelcomeCenterWeatherSummary(
                        welcomeCenterId,
                        average(weatherReports, WelcomeCenterWeatherReport::getTemperature),
                        average(weatherReports, WelcomeCenterWeatherReport::getHumidity),
                        average(weatherReports, WelcomeCenterWeatherReport::getBarometricPressure),
                        average(weatherReports, WelcomeCenterWeatherReport::getLuminosity),
                        LocalDateTime.now()));
    }

    public Mono<String> exportCsv(UUID welcomeCenterId) {
        return findByWelcomeCenterId(welcomeCenterId).collectList().map(weatherReports -> {
            StringBuilder csv = new StringBuilder(
                    "reportTime,callsign,longitude,latitude,temperature,humidity,barometricPressure,luminosity\r\n");
            weatherReports.forEach(report -> csv.append(csvValue(report.getReportTime()))
                    .append(',')
                    .append(csvValue(report.getCallsign()))
                    .append(',')
                    .append(csvValue(report.getLongitude()))
                    .append(',')
                    .append(csvValue(report.getLatitude()))
                    .append(',')
                    .append(csvValue(report.getTemperature()))
                    .append(',')
                    .append(csvValue(report.getHumidity()))
                    .append(',')
                    .append(csvValue(report.getBarometricPressure()))
                    .append(',')
                    .append(csvValue(report.getLuminosity()))
                    .append("\r\n"));
            return csv.toString();
        });
    }

    public Mono<WelcomeCenterWeatherReport> create(WelcomeCenterWeatherReport report) {
        return Mono.fromCallable(() -> {
                    report.setId(null);
                    return reports.save(report);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> delete(UUID id) {
        return findById(id)
                .flatMap(report ->
                        Mono.fromRunnable(() -> reports.delete(report)).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    public Mono<Void> deleteByCallsign(String callsign) {
        return Mono.fromRunnable(
                        () -> reports.deleteAll(reports.findByCallsignIgnoreCaseOrderByReportTimeDesc(callsign)))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<Long> deleteOlderThan(LocalDateTime cutoff) {
        return Mono.fromCallable(() -> {
                    var expiredReports = reports.findByReportTimeBefore(cutoff);
                    reports.deleteAll(expiredReports);
                    return (long) expiredReports.size();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void requireWelcomeCenter(UUID welcomeCenterId) {
        if (welcomeCenterId == null || !welcomeCenters.existsById(welcomeCenterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Welcome Center not found");
        }
    }

    private Float average(
            List<WelcomeCenterWeatherReport> weatherReports,
            Function<WelcomeCenterWeatherReport, Float> valueExtractor) {
        var values = weatherReports.stream()
                .map(valueExtractor)
                .filter(value -> value != null)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        return (float) values.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        return '"' + String.valueOf(value).replace("\"", "\"\"") + '"';
    }
}
