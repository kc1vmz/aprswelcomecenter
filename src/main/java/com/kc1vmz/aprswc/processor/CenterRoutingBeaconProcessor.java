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
package com.kc1vmz.aprswc.processor;

import com.kc1vmz.aprswc.accessor.PointOfInterestService;
import com.kc1vmz.aprswc.communication.*;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.object.*;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CenterRoutingBeaconProcessor {
    private final WelcomeCenterRepository centers;
    private final PointOfInterestService pois;
    private final ObjectBeaconQueue queue;

    public CenterRoutingBeaconProcessor(
            WelcomeCenterRepository centers, PointOfInterestService pois, ObjectBeaconQueue queue) {
        this.centers = centers;
        this.pois = pois;
        this.queue = queue;
    }

    @TransactionalEventListener
    public void routesAdded(CenterCommunicationRouting.AddedRoutes event) {
        advertise(event.centerId(), event.instanceIds());
    }

    @EventListener
    public void instanceAvailable(CenterCommunicationRouting.InstanceAvailable event) {
        for (var center : centers.findAll())
            if (CommunicationScope.of(center).allows(event.instanceId().toString()))
                advertise(center.getId(), Set.of(event.instanceId()));
    }

    private void advertise(UUID centerId, Set<UUID> targets) {
        try {
            var center = centers.findById(centerId).orElse(null);
            if (center == null || !center.isOpen()) return;
            if (center.getLatitude() != null && center.getLongitude() != null) {
                var beacon = new ObjectBeacon(
                        center.getCallsign(),
                        center.getCallsign(),
                        center.getLongitude(),
                        center.getLatitude(),
                        center.getSymbolCode(),
                        center.getSymbolId(),
                        "Welcome Center - " + Objects.toString(center.getName(), ""),
                        true,
                        CommunicationScope.of(center));
                beacon.setTargetInstanceIds(targets);
                beacon.setTransmissionPermitted(() -> centers.findById(centerId)
                        .filter(WelcomeCenter::isOpen)
                        .filter(c -> Objects.equals(c.getCallsign(), center.getCallsign()))
                        .isPresent());
                queue.offer(beacon);
            }
            for (var snapshot : pois.liveSnapshots()) {
                if (!snapshot.communicationScope().centerId().equals(centerId)) continue;
                var beacon = snapshot.beacon(true);
                beacon.setTargetInstanceIds(targets);
                beacon.setTransmissionPermitted(() -> pois.canSend(snapshot, true));
                queue.offer(beacon);
            }
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("Routing saved but immediate beacons failed for {}", centerId, failure);
        }
    }
}
