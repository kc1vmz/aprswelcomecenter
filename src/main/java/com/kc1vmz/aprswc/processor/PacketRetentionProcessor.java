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

import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.database.StationPacketRepository;
import com.kc1vmz.aprswc.database.StationPositionRepository;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Starts only after database migration and application initialization have completed. */
@Component
public class PacketRetentionProcessor {
    private static final Logger log = LoggerFactory.getLogger(PacketRetentionProcessor.class);
    private final ApplicationSettingsRepository settings;
    private final StationPacketRepository packets;
    private final StationPositionRepository positions;
    private final Clock clock;
    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "PacketRetentionProcessor"));

    @Autowired
    public PacketRetentionProcessor(
            ApplicationSettingsRepository settings,
            StationPacketRepository packets,
            StationPositionRepository positions) {
        this(settings, packets, positions, Clock.systemDefaultZone());
    }

    PacketRetentionProcessor(
            ApplicationSettingsRepository settings,
            StationPacketRepository packets,
            StationPositionRepository positions,
            Clock clock) {
        this.settings = settings;
        this.packets = packets;
        this.positions = positions;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        worker.scheduleWithFixedDelay(this::deleteExpiredPackets, 0, 24, TimeUnit.HOURS);
    }

    void deleteExpiredPackets() {
        try {
            // Read the current setting on every run so edits require no restart.
            var configuration = settings.findAll();
            Integer days = configuration.isEmpty()
                    ? ApplicationSettings.DEFAULT_PACKET_RETENTION_DAYS
                    : configuration.getFirst().getPacketRetentionDays();
            if (days == null || days <= 0) {
                log.error("Packet cleanup skipped: retention days must be greater than zero");
                return;
            }
            LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(days);
            int deleted = packets.deleteReceivedBefore(cutoff);
            int deletedPositions = positions.deleteCreatedBefore(cutoff);
            log.info(
                    "Retention cleanup deleted {} packets and {} positions before {} (retention: {} days)",
                    deleted,
                    deletedPositions,
                    cutoff,
                    days);
        } catch (RuntimeException failure) {
            // A failed run must not cancel subsequent scheduled executions.
            log.error("Packet retention cleanup failed; the next daily run will retry", failure);
        }
    }

    @PreDestroy
    public void stop() {
        worker.shutdownNow();
    }
}
