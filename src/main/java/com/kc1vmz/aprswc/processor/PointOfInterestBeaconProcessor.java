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
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PointOfInterestBeaconProcessor {
    private static final Logger log = LoggerFactory.getLogger(PointOfInterestBeaconProcessor.class);
    private final PointOfInterestService service;
    private final ObjectBeaconQueue queue;
    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PointOfInterestBeacons"));

    public PointOfInterestBeaconProcessor(PointOfInterestService service, ObjectBeaconQueue queue) {
        this.service = service;
        this.queue = queue;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        worker.scheduleWithFixedDelay(this::cycle, 60, 600, TimeUnit.SECONDS);
    }

    void cycle() {
        try {
            var snapshots = service.liveSnapshots();
            for (int i = 0; i < snapshots.size(); i++) {
                var snapshot = snapshots.get(i);
                worker.schedule(() -> offer(snapshot, true), i * 600000L / snapshots.size(), TimeUnit.MILLISECONDS);
            }
        } catch (RuntimeException failure) {
            log.error("POI beacon cycle failed", failure);
        }
    }

    @TransactionalEventListener
    public void afterCommit(PointOfInterestService.Changed event) {
        try {
            var old = event.previous();
            var current = event.current();
            if (old != null
                    && (current == null
                            || !current.up()
                            || !old.name().equals(current.name())
                            || !old.callsignFrom().equals(current.callsignFrom()))) offer(old, false);
            if (current != null && current.up()) offer(current, true);
        } catch (RuntimeException failure) {
            log.error("POI saved but its beacon could not be queued", failure);
        }
    }

    void offer(PointOfInterestService.Snapshot snapshot, boolean active) {
        var beacon = snapshot.beacon(active);
        beacon.setTransmissionPermitted(() -> service.canSend(snapshot, active));
        queue.offer(beacon);
    }

    @PreDestroy
    public void stop() {
        worker.shutdownNow();
    }
}
