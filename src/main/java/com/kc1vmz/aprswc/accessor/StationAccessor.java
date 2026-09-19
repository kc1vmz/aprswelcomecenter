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

import com.kc1vmz.aprswc.database.StationMessageRepository;
import com.kc1vmz.aprswc.database.StationPacketRepository;
import com.kc1vmz.aprswc.database.StationPositionRepository;
import com.kc1vmz.aprswc.database.StationRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.object.IgnoreStation;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import com.kc1vmz.aprswc.utils.GeoFenceUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class StationAccessor {
    @Autowired
    private StationRepository stations;

    @Autowired
    private StationPositionRepository positions;

    @Autowired
    private StationPacketRepository packets;

    @Autowired
    private StationMessageRepository messages;

    @Autowired
    private WelcomeCenterRepository welcomeCenters;

    @Autowired
    private GeoFenceUtils geoFenceUtils;

    @Autowired
    private IgnoreStationAccessor ignoreStations;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public Flux<Station> findAll() {
        return findAll(false);
    }

    public Flux<Station> findAll(boolean excludeIgnored) {
        return Mono.fromCallable(() -> {
                    Set<String> ignoredCallsigns = excludeIgnored ? ignoreStations.findAllCallsigns() : Set.of();
                    java.util.Map<String, java.time.Instant> lastHeard = new java.util.HashMap<>();
                    packets.findLastHeardByCallsign().forEach(packet -> {
                        if (packet.getCallsign() != null && packet.getReceivedTime() != null) {
                            lastHeard.put(
                                    packet.getCallsign(),
                                    packet.getReceivedTime()
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toInstant());
                        }
                    });
                    return stations.findAll().stream()
                            .peek(station -> station.setLastHeard(
                                    lastHeard.get(station.getCallsign().toUpperCase(Locale.ROOT))))
                            .filter(station -> !ignoredCallsigns.contains(
                                    station.getCallsign().toUpperCase(Locale.ROOT)))
                            .toList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<Station> findById(UUID id) {
        return Mono.fromCallable(() -> stations.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<Station> findByCallsign(String callsign) {
        return Mono.fromCallable(() -> stations.findByCallsignIgnoreCase(callsign))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<Station> create(Station value) {
        value.setId(null);
        return Mono.fromCallable(() -> stations.save(value)).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Station> replace(UUID id, Station value) {
        return findById(id)
                .then(Mono.fromCallable(() -> {
                            value.setId(id);
                            return stations.save(value);
                        })
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<Void> delete(UUID id) {
        return Mono.fromRunnable(() -> transactionTemplate.executeWithoutResult(status -> {
                    Station station =
                            stations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                    packets.deleteAllByCallsignIgnoreCase(station.getCallsign());
                    messages.deleteAllByCallsignToIgnoreCase(station.getCallsign());
                    stations.delete(station);
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<Void> deleteAll() {
        return Mono.fromRunnable(() -> transactionTemplate.executeWithoutResult(status -> {
                    stations.findAll().forEach(station -> {
                        packets.deleteAllByCallsignIgnoreCase(station.getCallsign());
                        messages.deleteAllByCallsignToIgnoreCase(station.getCallsign());
                    });
                    positions.deleteAll();
                    stations.deleteAll();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<IgnoreStation> ignore(UUID id) {
        return findById(id).flatMap(station -> ignoreStations.createIfAbsent(station.getCallsign()));
    }

    public Flux<StationPosition> findPositions(UUID stationId) {
        return findAllPositions(stationId);
    }

    public Flux<StationPosition> findAllPositions(UUID stationId) {
        return findById(stationId)
                .thenMany(Mono.fromCallable(() -> positions.findAllByStationId(stationId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    public Flux<StationPosition> findPositions(UUID stationId, int limit) {
        return findById(stationId).flatMapMany(station -> {
            if (limit <= 0) {
                return Flux.empty();
            }
            return Mono.fromCallable(() -> positions.findRecentByStationId(stationId, PageRequest.of(0, limit)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(Flux::fromIterable);
        });
    }

    public Flux<WelcomeCenter> findWelcomeCentersByLatestStationPosition(UUID stationId) {
        if (stationId == null) {
            return Flux.empty();
        }
        return findPositions(stationId, 1).next().flatMapMany(this::findWelcomeCentersByStationPosition);
    }

    public Flux<WelcomeCenter> findWelcomeCentersByStationPosition(StationPosition stationPosition) {
        if (stationPosition == null) {
            return Flux.empty();
        }
        return Mono.fromCallable(() -> {
                    List<WelcomeCenter> matches = new ArrayList<>();
                    for (WelcomeCenter welcomeCenter : welcomeCenters.findAll()) {
                        for (WelcomeRegion region : welcomeCenter.getRegions()) {
                            if (geoFenceUtils.isInGeoFenceRegion(region, stationPosition)) {
                                matches.add(welcomeCenter);
                                break;
                            }
                        }
                    }
                    return matches;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<StationPosition> addPosition(UUID stationId, StationPosition position) {
        return findById(stationId).flatMap(station -> Mono.fromCallable(() -> {
                    position.setId(null);
                    position.setStation(station);
                    return positions.save(position);
                })
                .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<StationPosition> replacePosition(UUID stationId, UUID positionId, StationPosition replacement) {
        return findById(stationId).flatMap(station -> Mono.fromCallable(() -> {
                    requirePosition(stationId, positionId);
                    replacement.setId(positionId);
                    replacement.setStation(station);
                    return positions.save(replacement);
                })
                .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<Void> deletePosition(UUID stationId, UUID positionId) {
        return Mono.fromRunnable(() -> transactionTemplate.executeWithoutResult(status -> {
                    StationPosition position = requirePosition(stationId, positionId);
                    positions.delete(position);
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<Void> deleteAllPositions(UUID stationId) {
        return findById(stationId)
                .then(Mono.fromRunnable(() -> positions.deleteAllByStationId(stationId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    private StationPosition requirePosition(UUID stationId, UUID positionId) {
        return positions
                .findById(positionId)
                .filter(position -> position.getStation().getId().equals(stationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
