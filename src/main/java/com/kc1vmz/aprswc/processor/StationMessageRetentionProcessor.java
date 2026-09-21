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

import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class StationMessageRetentionProcessor {
    private static final Logger log = LoggerFactory.getLogger(StationMessageRetentionProcessor.class);
    private final ApplicationSettingsRepository settings;
    private final StationRepository stations;
    private final StationPositionRepository positions;
    private final StationMessageRepository messages;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "StationMessageRetentionProcessor"));

    @Autowired
    public StationMessageRetentionProcessor(
            ApplicationSettingsRepository settings,
            StationRepository stations,
            StationPositionRepository positions,
            StationMessageRepository messages,
            TransactionTemplate transactions) {
        this(settings, stations, positions, messages, transactions, Clock.systemDefaultZone());
    }

    StationMessageRetentionProcessor(
            ApplicationSettingsRepository settings,
            StationRepository stations,
            StationPositionRepository positions,
            StationMessageRepository messages,
            TransactionTemplate transactions,
            Clock clock) {
        this.settings = settings;
        this.stations = stations;
        this.positions = positions;
        this.messages = messages;
        this.transactions = transactions;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        worker.scheduleWithFixedDelay(this::cleanup, 0, 24, TimeUnit.HOURS);
    }

    void cleanup() {
        ApplicationSettings config;
        try {
            config = settings.findAll().stream().findFirst().orElse(new ApplicationSettings(null, null));
        } catch (RuntimeException e) {
            log.error("Cannot read retention settings; next daily run will retry", e);
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            Integer days = config.getStationRetentionDays();
            if (days == null || days <= 0) throw new IllegalArgumentException("Station retention must be positive");
            LocalDateTime cutoff = now.minusDays(days);
            int deleted = 0;
            for (var id : stations.findExpiredIds(cutoff)) {
                Boolean removed = transactions.execute(status -> {
                    var station = stations.findForRetention(id).orElse(null);
                    // Recheck under a row lock: packets may have arrived since selecting candidates.
                    if (station == null || !station.getLastActivityTime().isBefore(cutoff)) return false;
                    positions.deleteAllByStationId(id);
                    stations.delete(station);
                    return true;
                });
                if (Boolean.TRUE.equals(removed)) deleted++;
            }
            log.info("Retention cleanup deleted {} stations inactive since before {}", deleted, cutoff);
        } catch (RuntimeException e) {
            log.error("Station retention failed; next daily run will retry", e);
        }
        try {
            Integer days = config.getMessageRetentionDays();
            if (days == null || days <= 0) throw new IllegalArgumentException("Message retention must be positive");
            LocalDateTime cutoff = now.minusDays(days);
            log.info(
                    "Retention cleanup deleted {} messages older than {}",
                    messages.deleteExpiredBefore(cutoff),
                    cutoff);
        } catch (RuntimeException e) {
            log.error("Message retention failed; next daily run will retry", e);
        }
    }

    @PreDestroy
    public void stop() {
        worker.shutdownNow();
    }
}
