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

import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.object.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PointOfInterestService {
    public record Edit(
            Long version,
            String name,
            String description,
            String latitude,
            String longitude,
            String symbolCode,
            String symbolTableId,
            boolean temporarilyUnavailable) {}

    public record View(
            UUID id,
            long version,
            UUID welcomeCenterId,
            String name,
            String description,
            String latitude,
            String longitude,
            String symbolCode,
            String symbolTableId,
            boolean temporarilyUnavailable,
            String status,
            List<String> downReasons) {}

    public record Snapshot(
            UUID id,
            long generation,
            String name,
            String description,
            String latitude,
            String longitude,
            String symbolCode,
            String symbolTableId,
            String callsignFrom,
            boolean up) {
        public ObjectBeacon beacon(boolean active) {
            return new ObjectBeacon(
                    name, callsignFrom, longitude, latitude, symbolCode, symbolTableId, description, active);
        }
    }

    public record Changed(Snapshot previous, Snapshot current) {}

    private final PointOfInterestRepository pois;
    private final WelcomeCenterRepository centers;
    private final JdbcTemplate jdbc;
    private final CoordinateAccessor coordinates;
    private final ApplicationEventPublisher events;

    public PointOfInterestService(
            PointOfInterestRepository pois,
            WelcomeCenterRepository centers,
            JdbcTemplate jdbc,
            CoordinateAccessor coordinates,
            ApplicationEventPublisher events) {
        this.pois = pois;
        this.centers = centers;
        this.jdbc = jdbc;
        this.coordinates = coordinates;
        this.events = events;
    }

    public void lockNames() {
        jdbc.queryForObject("select id from aprs_object_name_lock where id=1 for update", Integer.class);
    }

    public void validateCenterName(String callsign) {
        if (callsign != null
                && pois.findByName(callsign.toUpperCase(Locale.ROOT)).isPresent())
            throw error(HttpStatus.CONFLICT, "Callsign conflicts with a Point of Interest object name");
    }

    public static void validateSender(String callsign) {
        if (callsign == null || !callsign.toUpperCase(Locale.ROOT).matches("[A-Z0-9]{1,6}(-([0-9]|1[0-5]))?"))
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Welcome Center callsign must have 1-6 letters/digits and an optional SSID of 0-15 to transmit POIs");
    }

    private WelcomeCenter center(UUID id) {
        return centers.findForUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Welcome Center no longer exists"));
    }

    private PointOfInterest owned(UUID centerId, UUID id) {
        return pois.findById(id)
                .filter(p -> p.getWelcomeCenter().getId().equals(centerId))
                .orElseThrow(
                        () -> error(HttpStatus.NOT_FOUND, "Point of Interest no longer exists in this Welcome Center"));
    }

    @Transactional(readOnly = true)
    public List<View> list(UUID centerId) {
        if (!centers.existsById(centerId)) throw error(HttpStatus.NOT_FOUND, "Welcome Center no longer exists");
        return pois.findByWelcomeCenterIdOrderByName(centerId).stream()
                .map(this::view)
                .toList();
    }

    private View view(PointOfInterest p) {
        List<String> reasons = new ArrayList<>();
        if (!p.getWelcomeCenter().isOpen()) reasons.add("Welcome Center closed");
        if (p.isTemporarilyUnavailable()) reasons.add("Temporarily unavailable");
        return new View(
                p.getId(),
                p.getVersion(),
                p.getWelcomeCenter().getId(),
                p.getName(),
                p.getDescription(),
                p.getLatitude(),
                p.getLongitude(),
                p.getSymbolCode(),
                p.getSymbolTableId(),
                p.isTemporarilyUnavailable(),
                reasons.isEmpty() ? "UP" : "DOWN",
                reasons);
    }

    public Snapshot snapshot(PointOfInterest p) {
        return new Snapshot(
                p.getId(),
                p.getBeaconGeneration(),
                p.getName(),
                Objects.toString(p.getDescription(), ""),
                p.getLatitude(),
                p.getLongitude(),
                p.getSymbolCode(),
                p.getSymbolTableId(),
                p.getWelcomeCenter().getCallsign().toUpperCase(Locale.ROOT),
                p.getWelcomeCenter().isOpen() && !p.isTemporarilyUnavailable());
    }

    @Transactional
    public View save(UUID centerId, UUID id, Edit edit) {
        lockNames();
        var center = center(centerId);
        validateSender(center.getCallsign());
        var p = id == null ? new PointOfInterest() : owned(centerId, id);
        if (id != null && (edit.version() == null || edit.version() != p.getVersion()))
            throw error(
                    HttpStatus.CONFLICT, "Point of Interest or Welcome Center changed. Refresh and reopen the editor.");
        Snapshot previous = id == null ? null : snapshot(p);
        String name = Objects.toString(edit.name(), "").trim().toUpperCase(Locale.ROOT);
        if (!name.matches("[A-Z0-9]{1,9}"))
            throw error(HttpStatus.BAD_REQUEST, "Name must contain 1-9 letters or digits");
        if (centers.findByCallsignIgnoreCase(name).isPresent()
                || pois.findByName(name)
                        .filter(other -> !other.getId().equals(id))
                        .isPresent())
            throw error(HttpStatus.CONFLICT, "Object name is already used by a POI or Welcome Center");
        String description = Objects.toString(edit.description(), "");
        if (description.length() > 40 || !description.chars().allMatch(c -> c >= 32 && c <= 126))
            throw error(HttpStatus.BAD_REQUEST, "Description must be at most 40 printable ASCII characters");
        if (edit.symbolCode() == null || !edit.symbolCode().matches("[!-~]"))
            throw error(HttpStatus.BAD_REQUEST, "Symbol code must be one printable APRS symbol character");
        if (edit.symbolTableId() == null || !edit.symbolTableId().matches("[/\\\\A-Za-z0-9]"))
            throw error(HttpStatus.BAD_REQUEST, "Symbol table must be /, backslash, or a letter/digit overlay");
        p.setLatitude(coordinate(edit.latitude(), true));
        p.setLongitude(coordinate(edit.longitude(), false));
        if (id == null) {
            p.setId(UUID.randomUUID());
            p.setWelcomeCenter(center);
        }
        p.setName(name);
        p.setDescription(description);
        p.setSymbolCode(edit.symbolCode());
        p.setSymbolTableId(edit.symbolTableId());
        p.setTemporarilyUnavailable(edit.temporarilyUnavailable());
        p.setBeaconGeneration(p.getBeaconGeneration() + 1);
        p = pois.saveAndFlush(p);
        events.publishEvent(new Changed(previous, snapshot(p)));
        return view(p);
    }

    @Transactional
    public void delete(UUID centerId, UUID id, long version) {
        lockNames();
        center(centerId);
        var p = owned(centerId, id);
        if (p.getVersion() != version)
            throw error(HttpStatus.CONFLICT, "Point of Interest changed. Refresh and try again.");
        var previous = snapshot(p);
        pois.delete(p);
        pois.flush();
        events.publishEvent(new Changed(previous, null));
    }
    /** Capture before changing parent fields, inside the parent's transaction. */
    public List<Snapshot> beforeCenterChange(WelcomeCenter center) {
        return pois.findByWelcomeCenterIdOrderByName(center.getId()).stream()
                .map(this::snapshot)
                .toList();
    }

    public void afterCenterChange(WelcomeCenter center, List<Snapshot> previous) {
        if (previous.isEmpty()) return;
        validateSender(center.getCallsign());
        for (var old : previous) {
            var p = owned(center.getId(), old.id());
            p.setBeaconGeneration(p.getBeaconGeneration() + 1);
            pois.saveAndFlush(p);
            events.publishEvent(new Changed(old, snapshot(p)));
        }
    }

    public void deleteForCenter(WelcomeCenter center) {
        for (var p : pois.findByWelcomeCenterIdOrderByName(center.getId())) {
            var previous = snapshot(p);
            pois.delete(p);
            events.publishEvent(new Changed(previous, null));
        }
        pois.flush();
    }

    public boolean canSend(Snapshot expected, boolean active) {
        if (active)
            return pois.findById(expected.id())
                    .map(p -> {
                        var now = snapshot(p);
                        return now.up()
                                && now.generation() == expected.generation()
                                && now.callsignFrom().equals(expected.callsignFrom())
                                && now.name().equals(expected.name());
                    })
                    .orElse(false);
        // Do not let an old withdrawal hide a restored POI or a newly reused object name.
        if (pois.findByName(expected.name())
                .map(p -> snapshot(p).up()
                        && (!p.getId().equals(expected.id())
                                || snapshot(p).callsignFrom().equals(expected.callsignFrom())))
                .orElse(false)) return false;
        return centers.findByCallsignIgnoreCase(expected.name())
                .filter(WelcomeCenter::isOpen)
                .isEmpty();
    }

    public List<Snapshot> liveSnapshots() {
        return pois.findAll().stream().map(this::snapshot).filter(Snapshot::up).toList();
    }

    private String coordinate(String text, boolean latitude) {
        String value = Objects.toString(text, "").trim().toUpperCase(Locale.ROOT);
        int width = latitude ? 2 : 3, maximum = latitude ? 90 : 180;
        try {
            if (value.matches("[+-]?[0-9]+(\\.[0-9]+)?")) {
                var decimal = new BigDecimal(value);
                if (decimal.abs().compareTo(BigDecimal.valueOf(maximum)) > 0) throw new IllegalArgumentException();
                var result = coordinates.convertToAprsCoordinate(new DecimalCoordinatePair(
                        latitude ? BigDecimal.ZERO : decimal, latitude ? decimal : BigDecimal.ZERO));
                return latitude ? result.getLatitude() : result.getLongitude();
            }
            if (!value.matches(latitude ? "[0-9]{4}\\.[0-9]{2}[NS]" : "[0-9]{5}\\.[0-9]{2}[EW]"))
                throw new IllegalArgumentException();
            int degrees = Integer.parseInt(value.substring(0, width));
            double minutes = Double.parseDouble(value.substring(width, value.length() - 1));
            if (degrees > maximum || minutes >= 60 || (degrees == maximum && minutes != 0))
                throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException failure) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    (latitude ? "Latitude" : "Longitude") + " must be valid APRS notation or decimal degrees");
        }
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
