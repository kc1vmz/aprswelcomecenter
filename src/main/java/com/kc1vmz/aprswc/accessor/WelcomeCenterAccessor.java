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

import com.kc1vmz.aprswc.communication.CenterCommunicationRouting;
import com.kc1vmz.aprswc.communication.CommunicationScope;
import com.kc1vmz.aprswc.constants.AprsSymbols;
import com.kc1vmz.aprswc.constants.ObjectSymbolTableConstants;
import com.kc1vmz.aprswc.database.StationPositionRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.database.WelcomeRegionRepository;
import com.kc1vmz.aprswc.enumeration.WelcomeCenterStatus;
import com.kc1vmz.aprswc.object.ObjectBeacon;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeCenterChanged;
import com.kc1vmz.aprswc.object.WelcomeCenterSnapshot;
import com.kc1vmz.aprswc.object.WelcomeCenterStatusChange;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import com.kc1vmz.aprswc.processor.ObjectBeaconQueue;
import com.kc1vmz.aprswc.utils.GeoFenceUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
    private PointOfInterestService pois;

    @Autowired
    private CenterCommunicationRouting routing;

    @Autowired
    private ContainmentDeletionService deletions;

    @Autowired
    private GeoFenceUtils geoFenceUtils;

    @Autowired
    private ObjectBeaconQueue objectBeaconQueue;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ApplicationEventPublisher events;

    private static final String STATUS_MESSAGE = "Welcome Center - %s - %s";

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

    public Mono<WelcomeCenter> findOpenById(UUID id) {
        return findById(id)
                .filter(WelcomeCenter::isOpen)
                .onErrorResume(
                        ResponseStatusException.class,
                        error -> error.getStatusCode() == HttpStatus.NOT_FOUND ? Mono.empty() : Mono.error(error));
    }

    public Mono<WelcomeCenter> findOpenByCallsign(String callsign) {
        return findByCallsign(callsign)
                .filter(WelcomeCenter::isOpen)
                .onErrorResume(
                        ResponseStatusException.class,
                        error -> error.getStatusCode() == HttpStatus.NOT_FOUND ? Mono.empty() : Mono.error(error));
    }

    public Mono<WelcomeCenter> create(WelcomeCenter value) {
        if (value.getSymbolCode() == null) value.setSymbolCode(ObjectSymbolTableConstants.DEFAULT_SYMBOL_TABLE_CODE);
        if (value.getSymbolId() == null) value.setSymbolId(ObjectSymbolTableConstants.DEFAULT_SYMBOL_TABLE_ID);
        AprsSymbols.validate(value.getSymbolId(), value.getSymbolCode(), null, null);
        value.setId(null);
        if (value.getStatus() == null) value.setStatus(WelcomeCenterStatus.OPEN);
        return Mono.fromCallable(() -> transactions.execute(tx -> {
                    pois.lockNames();
                    pois.validateCenterName(value.getCallsign());
                    routing.validate(value);
                    value.setRoutingVersion(0);
                    return centers.saveAndFlush(value);
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .map(createdWelcomeCenter -> {
                    afterWelcomeCenterCreated(createdWelcomeCenter);
                    return createdWelcomeCenter;
                });
    }

    private void afterWelcomeCenterCreated(WelcomeCenter welcomeCenter) {
        if (welcomeCenter.isOpen() && (welcomeCenter.getLatitude() != null) && (welcomeCenter.getLongitude() != null)) {
            String statusMessage = String.format(
                    STATUS_MESSAGE,
                    welcomeCenter.getName(),
                    (welcomeCenter.getDescription() != null) ? welcomeCenter.getDescription() : "");
            ObjectBeacon objectBeacon = new ObjectBeacon(
                    welcomeCenter.getCallsign(),
                    welcomeCenter.getCallsign(),
                    welcomeCenter.getLongitude(),
                    welcomeCenter.getLatitude(),
                    welcomeCenter.getSymbolCode(),
                    welcomeCenter.getSymbolId(),
                    statusMessage,
                    true,
                    CommunicationScope.of(welcomeCenter));
            objectBeaconQueue.offer(objectBeacon);
        }
    }

    public Mono<WelcomeCenter> replace(UUID id, WelcomeCenter value) {
        if (value.getStatus() == null)
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required"));
        return Mono.fromCallable(() -> transactions.execute(transaction -> {
                    pois.lockNames();
                    WelcomeCenter existing = centers.findForUpdate(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                    var previous = WelcomeCenterSnapshot.of(existing);
                    var previousPois = pois.beforeCenterChange(existing);
                    routing.update(existing, value);
                    existing.setStatus(value.getStatus());
                    existing.setName(value.getName());
                    existing.setDescription(value.getDescription());
                    pois.validateCenterName(value.getCallsign());
                    existing.setCallsign(value.getCallsign());
                    existing.setOwnerName(value.getOwnerName());
                    existing.setOwnerCallsign(value.getOwnerCallsign());
                    existing.setOrganizationName(value.getOrganizationName());
                    existing.setOrganizationCallsign(value.getOrganizationCallsign());
                    existing.setContactName(value.getContactName());
                    existing.setContactCallsign(value.getContactCallsign());
                    existing.setLongitude(value.getLongitude());
                    existing.setLatitude(value.getLatitude());
                    String symbolCode =
                            value.getSymbolCode() == null ? existing.getSymbolCode() : value.getSymbolCode();
                    String symbolId = value.getSymbolId() == null ? existing.getSymbolId() : value.getSymbolId();
                    if (symbolCode == null) symbolCode = ObjectSymbolTableConstants.DEFAULT_SYMBOL_TABLE_CODE;
                    if (symbolId == null) symbolId = ObjectSymbolTableConstants.DEFAULT_SYMBOL_TABLE_ID;
                    AprsSymbols.validate(symbolId, symbolCode, existing.getSymbolId(), existing.getSymbolCode());
                    existing.setSymbolCode(symbolCode);
                    existing.setSymbolId(symbolId);
                    WelcomeCenter saved = centers.saveAndFlush(existing);
                    if (previous.status() != saved.getStatus()
                            || !previous.callsign().equalsIgnoreCase(saved.getCallsign()))
                        pois.afterCenterChange(saved, previousPois);
                    events.publishEvent(new WelcomeCenterChanged(previous, WelcomeCenterSnapshot.of(saved)));
                    return saved;
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<WelcomeCenter> changeStatus(UUID id, WelcomeCenterStatusChange change) {
        return Mono.fromCallable(() -> transactions.execute(transaction -> {
                    pois.lockNames();
                    WelcomeCenter existing = centers.findForUpdate(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                    if (existing.getStatus() != change.expectedStatus()) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Welcome Center status has changed. Refresh and try again.");
                    }
                    if (existing.getStatus() == change.status()) return existing;
                    var previous = WelcomeCenterSnapshot.of(existing);
                    var previousPois = pois.beforeCenterChange(existing);
                    existing.setStatus(change.status());
                    WelcomeCenter saved = centers.saveAndFlush(existing);
                    if (previous.status() != saved.getStatus()
                            || !previous.callsign().equalsIgnoreCase(saved.getCallsign()))
                        pois.afterCenterChange(saved, previousPois);
                    events.publishEvent(new WelcomeCenterChanged(previous, WelcomeCenterSnapshot.of(saved)));
                    return saved;
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> delete(UUID id) {
        return Mono.fromCallable(() -> deletions.deleteWelcomeCenter(id))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(this::beforeWelcomeCenterDelete)
                .then();
    }

    private void beforeWelcomeCenterDelete(WelcomeCenter welcomeCenter) {
        if ((welcomeCenter.getLatitude() != null) && (welcomeCenter.getLongitude() != null)) {
            String statusMessage = String.format(
                    STATUS_MESSAGE,
                    welcomeCenter.getName(),
                    java.util.Objects.toString(welcomeCenter.getDescription(), ""));
            ObjectBeacon objectBeacon = new ObjectBeacon(
                    welcomeCenter.getCallsign(),
                    welcomeCenter.getCallsign(),
                    welcomeCenter.getLongitude(),
                    welcomeCenter.getLatitude(),
                    welcomeCenter.getSymbolCode(),
                    welcomeCenter.getSymbolId(),
                    statusMessage,
                    false,
                    CommunicationScope.of(welcomeCenter));
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
