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

import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.database.StationPacketRepository;
import com.kc1vmz.aprswc.database.StationRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class StationPacketAccessor {
    private final ApplicationSettingsRepository settings;
    private final StationPacketRepository repository;
    private final StationRepository stations;
    private final StationPacketQueue queue;
    private final WelcomeCenterRepository welcomeCenterRepository;

    public StationPacketAccessor(
            StationPacketRepository repository,
            StationRepository stations,
            StationPacketQueue queue,
            WelcomeCenterRepository welcomeCenterRepository,
            ApplicationSettingsRepository settings) {
        this.settings = settings;
        this.repository = repository;
        this.stations = stations;
        this.queue = queue;
        this.welcomeCenterRepository = welcomeCenterRepository;
    }

    private StationPacket saveRawHistory(StationPacket value) {
        if (!settings.findAll().stream()
                .findFirst()
                .map(ApplicationSettings::isPacketLoggingEnabled)
                .orElse(false)) return value;
        String command = value.getCommand();
        int bodyStart = command == null ? -1 : command.indexOf(':');
        if (bodyStart >= 0 && command.substring(bodyStart + 1).contains("!x!")) return value;
        return repository.save(value);
    }

    public boolean isWelcomeCenterCallsign(String callsign) {
        if (callsign == null) {
            return false;
        }
        return welcomeCenterRepository.findAll().stream()
                .anyMatch(welcomeCenter -> callsign.equalsIgnoreCase(welcomeCenter.getCallsign()));
    }

    public Flux<StationPacket> findAll() {
        return Mono.fromCallable(repository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Flux<StationPacket> findHistory(String callsign) {
        return callsign == null ? findAll() : findAll().filter(packet -> pertainsTo(packet, callsign));
    }

    static boolean pertainsTo(StationPacket packet, String callsign) {
        String raw = packet.getCommand();
        if (raw == null || callsign.isBlank()) return false;
        int sourceEnd = raw.indexOf('>');
        int bodyStart = raw.indexOf(':', sourceEnd + 1);
        if (sourceEnd < 1 || bodyStart < 0) return false;
        String body = raw.substring(bodyStart + 1);
        String recipient = body.length() >= 11 && body.charAt(0) == ':' && body.charAt(10) == ':'
                ? body.substring(1, 10).trim()
                : "";
        if (recipient.toUpperCase(Locale.ROOT).startsWith("BLN")) return false;
        String selected = callsign.trim();
        return raw.substring(0, sourceEnd).equalsIgnoreCase(selected) || recipient.equalsIgnoreCase(selected);
    }

    public Flux<StationPacket> findAllByCallsign(String callsign) {
        if ((callsign == null) || callsign.isBlank()) {
            return Flux.empty();
        }
        return Mono.fromCallable(() -> repository.findHeardRoutes(callsign))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(route -> new StationPacket(
                        null, route.getPacketProcessorId(), route.getCallsign(), route.getReceivedTime(), null, null));
    }

    public Mono<StationPacket> findById(UUID id) {
        return Mono.fromCallable(() -> repository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<String> exportCsv() {
        return exportCsv(null);
    }

    public Mono<String> exportCsv(String callsign) {
        return findHistory(callsign)
                .sort(Comparator.comparing(
                        StationPacket::getReceivedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collectList()
                .map(packets -> {
                    StringBuilder csv =
                            new StringBuilder("time,direction,communicationInstanceId,callsign,command\r\n");
                    packets.forEach(packet -> csv.append(csvValue(packet.getReceivedTime()))
                            .append(',')
                            .append(csvValue(packet.getDirection()))
                            .append(',')
                            .append(csvValue(packet.getPacketProcessorId()))
                            .append(',')
                            .append(csvValue(packet.getCallsign()))
                            .append(',')
                            .append(csvValue(packet.getCommand()))
                            .append("\r\n"));
                    return csv.toString();
                });
    }

    /** Saves outgoing history without incoming processing or station activity updates. */
    public void recordTransmitted(StationPacket packet) {
        saveRawHistory(packet);
    }

    public Mono<StationPacket> create(StationPacket value) {
        value.setId(null);
        return Mono.fromCallable(() -> saveRawHistory(value))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(queue::offer);
    }

    public Mono<StationPacket> save(StationPacket value) {
        return Mono.fromCallable(() -> {
                    if ("RX".equals(value.getDirection())
                            && value.getCallsign() != null
                            && value.getPacketProcessorId() != null
                            && value.getReceivedTime() != null) {
                        repository.recordRoute(
                                value.getCallsign().toUpperCase(Locale.ROOT),
                                value.getPacketProcessorId(),
                                value.getReceivedTime());
                    }
                    var saved = saveRawHistory(value);
                    if (saved.getCallsign() != null && saved.getReceivedTime() != null)
                        stations.recordActivity(saved.getCallsign(), saved.getReceivedTime());
                    return saved;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<StationPacket> replace(UUID id, StationPacket value) {
        return findById(id)
                .then(Mono.fromCallable(() -> {
                            value.setId(id);
                            return saveRawHistory(value);
                        })
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<Void> delete(UUID id) {
        return findById(id)
                .then(Mono.fromRunnable(() -> repository.deleteById(id)).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    public Mono<Void> deleteAll() {
        return Mono.fromRunnable(repository::deleteAll)
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        return '"' + String.valueOf(value).replace("\"", "\"\"") + '"';
    }
}
