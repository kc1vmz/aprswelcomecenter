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
package com.kc1vmz.aprswc.communication;

import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CenterCommunicationRouting {
    private final WelcomeCenterRepository centers;
    private final CommunicationInstanceRepository instances;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public CenterCommunicationRouting(
            WelcomeCenterRepository centers,
            CommunicationInstanceRepository instances,
            JdbcTemplate jdbc,
            ApplicationEventPublisher events) {
        this.centers = centers;
        this.instances = instances;
        this.jdbc = jdbc;
        this.events = events;
    }

    public record AddedRoutes(UUID centerId, Set<UUID> instanceIds) {}

    public record InstanceAvailable(UUID instanceId) {}

    // Same lock order as Welcome Center / POI edits: global configuration lock, then parent rows.
    public void lockConfiguration() {
        jdbc.queryForObject("select id from aprs_object_name_lock where id=1 for update", Integer.class);
    }

    public void validate(WelcomeCenter center) {
        if (!Set.of("ALL", "SELECTED").contains(Objects.toString(center.getCommunicationMode(), "")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select ALL or SELECTED communication methods");
        if (center.getCommunicationInstanceIds() == null
                || center.getCommunicationInstanceIds().stream().anyMatch(Objects::isNull))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Communication instance IDs are required");
        if ("ALL".equals(center.getCommunicationMode())) {
            center.setCommunicationInstanceIds(new HashSet<>());
            return;
        }
        if (instances.findAllById(center.getCommunicationInstanceIds()).size()
                != center.getCommunicationInstanceIds().size())
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A selected communication method was deleted. Refresh and review the selection.");
    }

    public void update(WelcomeCenter existing, WelcomeCenter value) {
        if (existing.getRoutingVersion() != value.getRoutingVersion())
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Communication selection changed. Refresh and review the Welcome Center.");
        validate(value);
        var previous = CommunicationScope.of(existing);
        boolean changed = !existing.getCommunicationMode().equals(value.getCommunicationMode())
                || !existing.getCommunicationInstanceIds().equals(value.getCommunicationInstanceIds());
        if (!changed) return;
        existing.setCommunicationMode(value.getCommunicationMode());
        existing.getCommunicationInstanceIds().clear();
        existing.getCommunicationInstanceIds().addAll(value.getCommunicationInstanceIds());
        existing.setRoutingVersion(existing.getRoutingVersion() + 1);
        var current = CommunicationScope.of(existing);
        var added = new HashSet<UUID>();
        for (var instance : instances.findAll())
            if (current.allows(instance.getId().toString())
                    && !previous.allows(instance.getId().toString())) added.add(instance.getId());
        if (!added.isEmpty()) events.publishEvent(new AddedRoutes(existing.getId(), Set.copyOf(added)));
    }

    public void beforeInstanceDeleted(UUID id) {
        jdbc.update(
                "update welcome_centers set routing_version=routing_version+1 where id in (select welcome_center_id from welcome_center_communications where communication_instance_id=?)",
                id);
        // Foreign key cascade removes only associations. SELECTED stays SELECTED, even when empty.
    }

    public boolean permits(CommunicationScope scope, String instanceId, boolean deletedParentWithdrawal) {
        if (scope == null) return true;
        return centers.findById(scope.centerId())
                .map(c -> CommunicationScope.of(c).allows(instanceId))
                .orElseGet(() -> deletedParentWithdrawal && scope.allows(instanceId));
    }

    public boolean permits(UUID centerId, String instanceId) {
        return centerId == null
                || centers.findById(centerId)
                        .map(c -> CommunicationScope.of(c).allows(instanceId))
                        .orElse(false);
    }

    public UUID senderCenter(String callsign) {
        return callsign == null
                ? null
                : centers.findByCallsignIgnoreCase(callsign)
                        .map(WelcomeCenter::getId)
                        .orElse(null);
    }
}
