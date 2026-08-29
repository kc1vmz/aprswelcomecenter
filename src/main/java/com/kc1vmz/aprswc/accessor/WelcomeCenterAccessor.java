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
import com.kc1vmz.aprswc.database.StationPositionRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.database.WelcomeRegionRepository;
import com.kc1vmz.aprswc.object.ObjectBeacon;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import com.kc1vmz.aprswc.processor.ObjectBeaconQueue;
import com.kc1vmz.aprswc.utils.GeoFenceUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class WelcomeCenterAccessor {
    @Autowired
    private WelcomeCenterRepository centers;

    @Autowired
    private WelcomeRegionRepository regions;

    @Autowired
    private StationPositionRepository stationPositions;

    @Autowired
    private StationMessageRepository stationMessages;

    @Autowired
    private GeoFenceUtils geoFenceUtils;

    @Autowired
    private ObjectBeaconQueue objectBeaconQueue;

    private static final String STATUS_MESSAGE = "APRS Welcome Center";

    public Flux<WelcomeCenter> findAll() {
        return Mono.fromCallable(centers::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<WelcomeCenter> findById(UUID id) {
        return Mono.fromCallable(() -> centers.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<WelcomeCenter> findByCallsign(String callsign) {
        return Mono.fromCallable(() ->
                        callsign == null ? Optional.<WelcomeCenter>empty() : centers.findByCallsignIgnoreCase(callsign))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<WelcomeCenter> create(WelcomeCenter value) {
        value.setId(null);
        return Mono.fromCallable(() -> centers.save(value))
                .subscribeOn(Schedulers.boundedElastic())
                .map(createdWelcomeCenter -> {
                    afterWelcomeCenterCreated(createdWelcomeCenter);
                    return createdWelcomeCenter;
                });
    }

    private void afterWelcomeCenterCreated(WelcomeCenter welcomeCenter) {
        if ((welcomeCenter.getLatitude() != null) && (welcomeCenter.getLongitude() != null)) {
            String statusMessage = String.format(STATUS_MESSAGE);
            ObjectBeacon objectBeacon = new ObjectBeacon(
                    welcomeCenter.getCallsign(),
                    welcomeCenter.getOwnerCallsign(),
                    welcomeCenter.getLongitude(),
                    welcomeCenter.getLatitude(),
                    welcomeCenter.getSymbolCode(),
                    welcomeCenter.getSymbolId(),
                    statusMessage,
                    true);
            objectBeaconQueue.offer(objectBeacon);
        }
    }

    public Mono<WelcomeCenter> replace(UUID id, WelcomeCenter value) {
        return findById(id).flatMap(existing -> Mono.fromCallable(() -> {
                    beforeWelcomeCenterReplace(existing, value);
                    existing.setName(value.getName());
                    existing.setDescription(value.getDescription());
                    existing.setCallsign(value.getCallsign());
                    existing.setOwnerName(value.getOwnerName());
                    existing.setOwnerCallsign(value.getOwnerCallsign());
                    existing.setOrganizationName(value.getOrganizationName());
                    existing.setOrganizationCallsign(value.getOrganizationCallsign());
                    existing.setContactName(value.getContactName());
                    existing.setContactCallsign(value.getContactCallsign());
                    existing.setLongitude(value.getLongitude());
                    existing.setLatitude(value.getLatitude());
                    existing.setSymbolCode(value.getSymbolCode());
                    existing.setSymbolId(value.getSymbolId());
                    return centers.save(existing);
                })
                .subscribeOn(Schedulers.boundedElastic()));
    }

    private void beforeWelcomeCenterReplace(WelcomeCenter existing, WelcomeCenter proposed) {
        if ((existing == null) || (proposed == null)) {
            return;
        }
        if (!existing.getCallsign().equalsIgnoreCase(proposed.getCallsign())) {
            // need to down and up the objects
            String statusMessageDown = String.format(STATUS_MESSAGE);
            ObjectBeacon objectBeaconDown = new ObjectBeacon(
                    existing.getCallsign(),
                    existing.getOwnerCallsign(),
                    existing.getLongitude(),
                    existing.getLatitude(),
                    existing.getSymbolCode(),
                    existing.getSymbolId(),
                    statusMessageDown,
                    false);
            objectBeaconQueue.offer(objectBeaconDown);
            String statusMessageUp = String.format(STATUS_MESSAGE);
            ObjectBeacon objectBeaconUp = new ObjectBeacon(
                    proposed.getCallsign(),
                    proposed.getOwnerCallsign(),
                    proposed.getLongitude(),
                    proposed.getLatitude(),
                    proposed.getSymbolCode(),
                    proposed.getSymbolId(),
                    statusMessageUp,
                    true);
            objectBeaconQueue.offer(objectBeaconUp);
        }
    }

    public Mono<Void> delete(UUID id) {
        return findById(id)
                .flatMap(welcomeCenter -> Mono.fromRunnable(() -> {
                            beforeWelcomeCenterDelete(welcomeCenter);
                            stationMessages.deleteAllByWelcomeCenterId(id);
                            centers.deleteById(id);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .then())
                .then();
    }

    private void beforeWelcomeCenterDelete(WelcomeCenter welcomeCenter) {
        if ((welcomeCenter.getLatitude() != null) && (welcomeCenter.getLongitude() != null)) {
            String statusMessage = String.format(STATUS_MESSAGE);
            ObjectBeacon objectBeacon = new ObjectBeacon(
                    welcomeCenter.getCallsign(),
                    welcomeCenter.getOwnerCallsign(),
                    welcomeCenter.getLongitude(),
                    welcomeCenter.getLatitude(),
                    welcomeCenter.getSymbolCode(),
                    welcomeCenter.getSymbolId(),
                    statusMessage,
                    false);
            objectBeaconQueue.offer(objectBeacon);
        }
    }

    public Flux<WelcomeRegion> findRegions(UUID centerId) {
        return findById(centerId).flatMapMany(center -> Flux.fromIterable(center.getRegions()));
    }

    public Flux<StationPosition> findStationPositions(UUID centerId) {
        return findById(centerId).flatMapMany(center -> Mono.fromCallable(() -> {
                    List<StationPosition> matchingPositions = new ArrayList<>();
                    List<StationPosition> allPositions = stationPositions.findLatestByCallsign();
                    for (StationPosition stationPosition : allPositions) {
                        for (WelcomeRegion welcomeRegion : center.getRegions()) {
                            if (isStationPositionInWelcomeRegion(stationPosition, welcomeRegion)) {
                                matchingPositions.add(stationPosition);
                                break;
                            }
                        }
                    }
                    return matchingPositions;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable));
    }

    public boolean isStationPositionInWelcomeRegion(StationPosition stationPosition, WelcomeRegion welcomeRegion) {
        return geoFenceUtils.isInGeoFenceRegion(welcomeRegion, stationPosition);
    }

    public Mono<WelcomeRegion> addRegion(UUID centerId, WelcomeRegion region) {
        return findById(centerId).flatMap(center -> Mono.fromCallable(() -> {
                    region.setId(null);
                    center.addRegion(region);
                    centers.save(center);
                    return region;
                })
                .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<WelcomeRegion> replaceRegion(UUID centerId, UUID regionId, WelcomeRegion replacement) {
        return findById(centerId).flatMap(center -> Mono.fromCallable(() -> {
                    requireRegion(centerId, regionId);
                    replacement.setId(regionId);
                    replacement.setWelcomeCenter(center);
                    return regions.save(replacement);
                })
                .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<Void> deleteRegion(UUID centerId, UUID regionId) {
        return findById(centerId).flatMap(center -> Mono.fromRunnable(() -> {
                    WelcomeRegion region = center.getRegions().stream()
                            .filter(candidate -> candidate.getId().equals(regionId))
                            .findFirst()
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                    center.removeRegion(region);
                    centers.save(center);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then());
    }

    private WelcomeRegion requireRegion(UUID centerId, UUID regionId) {
        return regions.findById(regionId)
                .filter(region -> region.getWelcomeCenter().getId().equals(centerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
