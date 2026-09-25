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

import com.kc1vmz.aprswc.database.CommunicationInstanceRepository;
import com.kc1vmz.aprswc.object.*;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import com.kc1vmz.aprswc.processor.aprs.is.*;
import com.kc1vmz.aprswc.processor.aprs.kiss.*;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CommunicationInstanceManager {
    @Autowired
    private CenterCommunicationRouting routing;

    public record Health(String status, String lastError, LocalDateTime lastPacketTime) {}

    private final CommunicationInstanceRepository repository;
    private final StationPacketQueue packets;
    private final APRSUtilityAccessor utility;
    private final Map<UUID, ManagedPacketListener> workers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> new Thread(r, "Communication-maintenance"));
    private volatile boolean running;

    public CommunicationInstanceManager(
            CommunicationInstanceRepository repository, StationPacketQueue packets, APRSUtilityAccessor utility) {
        this.repository = repository;
        this.packets = packets;
        this.utility = utility;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running = true;
        reconcile();
        scheduler.scheduleWithFixedDelay(
                () -> workers.values().forEach(ManagedPacketListener::checkWriteTimeout), 1, 1, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        reconcile();
                    } catch (RuntimeException e) {
                        org.slf4j.LoggerFactory.getLogger(getClass()).error("Communication reconciliation failed", e);
                    }
                },
                1,
                1,
                TimeUnit.SECONDS);
    }

    public synchronized void reconcile() {
        if (!running) return;
        Map<UUID, CommunicationInstance> desired = new HashMap<>();
        repository.findAll().forEach(c -> desired.put(c.getId(), c));
        for (var entry : workers.entrySet()) {
            var c = desired.get(entry.getKey());
            var w = entry.getValue();
            if (c == null || !"ACTIVE".equals(c.getState()) || c.getVersion() != w.config.version()) {
                if (w.stop()) workers.remove(entry.getKey(), w);
            }
        }
        for (var c : desired.values()) {
            if (!"ACTIVE".equals(c.getState()) || workers.containsKey(c.getId())) continue;
            // A failed stop must retain exclusive ownership of the serial device.
            if ("KISS_SERIAL".equals(c.getType())
                    && c.getSerialDevice() != null
                    && workers.values().stream()
                            .anyMatch(w -> "KISS_SERIAL".equals(w.config.type())
                                    && c.getSerialDevice().equalsIgnoreCase(w.config.serialDevice()))) continue;
            var config = CommunicationConfig.from(c);
            ManagedPacketListener worker = "APRS_IS".equals(c.getType())
                    ? new PacketListenerInternetServer(config, packets, utility)
                    : new PacketListenerKISS(config, packets);
            workers.put(c.getId(), worker);
            worker.start();
        }
    }

    public Health health(UUID id) {
        var w = workers.get(id);
        return w == null
                ? new Health("STOPPED", null, null)
                : new Health(w.health(), w.lastError(), w.lastPacketTime());
    }

    public boolean sendMessage(String id, String from, String to, String content, Runnable onSent) {
        return sendMessage(id, from, to, content, () -> true, onSent);
    }

    public boolean sendMessage(
            String id, String from, String to, String content, BooleanSupplier permitted, Runnable onSent) {
        if (id == null) return false;
        try {
            var w = workers.get(UUID.fromString(id));
            UUID owner = routing.senderCenter(from);
            return w != null
                    && w.send(
                            t -> t.sendMessage(from, to, content),
                            () -> permitted.getAsBoolean() && routing.permits(owner, id),
                            onSent);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public void sendBulletin(String from, String to, String content) {
        sendBulletin(null, from, to, content, () -> true);
    }

    public boolean isEligible(UUID centerId, String instanceId) {
        return routing.permits(centerId, instanceId);
    }

    public UUID centerIdForSender(String callsign) {
        return routing.senderCenter(callsign);
    }

    public void sendBulletin(UUID centerId, String from, String to, String content, BooleanSupplier permitted) {
        UUID owner = centerId == null ? routing.senderCenter(from) : centerId;
        workers.forEach((id, w) -> w.send(
                t -> t.sendMessage(from, to, content),
                () -> permitted.getAsBoolean() && routing.permits(owner, id.toString()),
                () -> {}));
    }

    public void sendObject(ObjectBeacon beacon) {
        workers.forEach((id, w) -> {
            if (beacon.getTargetInstanceIds() != null
                    && !beacon.getTargetInstanceIds().contains(id)) return;
            w.send(
                    t -> t.sendObject(beacon),
                    () -> beacon.isTransmissionPermitted()
                            && routing.permits(beacon.getCommunicationScope(), id.toString(), !beacon.isActive()),
                    () -> {});
        });
    }

    @PreDestroy
    public synchronized void stop() {
        running = false;
        scheduler.shutdownNow();
        workers.values().forEach(ManagedPacketListener::stop);
    }
}
