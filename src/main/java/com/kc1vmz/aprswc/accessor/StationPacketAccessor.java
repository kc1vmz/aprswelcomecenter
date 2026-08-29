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

import com.kc1vmz.aprswc.database.StationPacketRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class StationPacketAccessor {
    @Autowired
    private StationPacketRepository repository;

    @Autowired
    private StationPacketQueue queue;

    @Autowired
    private WelcomeCenterRepository welcomeCenterRepository;

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

    public Flux<StationPacket> findAllByCallsign(String callsign) {
        if ((callsign == null) || callsign.isBlank()) {
            return Flux.empty();
        }
        return Mono.fromCallable(() -> repository.findAllByCallsignIgnoreCaseOrderByReceivedTimeDesc(callsign))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<StationPacket> findById(UUID id) {
        return Mono.fromCallable(() -> repository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<String> exportCsv() {
        return findAll()
                .sort(Comparator.comparing(
                        StationPacket::getReceivedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collectList()
                .map(packets -> {
                    StringBuilder csv = new StringBuilder("receivedTime,callsign,command\r\n");
                    packets.forEach(packet -> csv.append(csvValue(packet.getReceivedTime()))
                            .append(',')
                            .append(csvValue(packet.getCallsign()))
                            .append(',')
                            .append(csvValue(packet.getCommand()))
                            .append("\r\n"));
                    return csv.toString();
                });
    }

    public Mono<StationPacket> create(StationPacket value) {
        value.setId(null);
        return Mono.fromCallable(() -> repository.save(value))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(queue::offer);
    }

    public Mono<StationPacket> save(StationPacket value) {
        return Mono.fromCallable(() -> repository.save(value)).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<StationPacket> replace(UUID id, StationPacket value) {
        return findById(id)
                .then(Mono.fromCallable(() -> {
                            value.setId(id);
                            return repository.save(value);
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
