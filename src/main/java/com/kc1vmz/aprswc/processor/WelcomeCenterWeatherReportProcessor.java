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

import com.kc1vmz.aprswc.accessor.WelcomeCenterWeatherReportAccessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WelcomeCenterWeatherReportProcessor {
    private static final Logger log = LoggerFactory.getLogger(WelcomeCenterWeatherReportProcessor.class);
    private static final long CLEANUP_INTERVAL_MINUTES = 10;
    private static final long REPORT_RETENTION_HOURS = 1;

    @Autowired
    private WelcomeCenterWeatherReportAccessor accessor;

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            runnable -> new Thread(runnable, "WelcomeCenterWeatherReportProcessor"));

    @PostConstruct
    void start() {
        worker.scheduleWithFixedDelay(this::deleteExpiredReports, 0, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    void deleteExpiredReports() {
        try {
            Long deleted = accessor.deleteOlderThan(LocalDateTime.now().minusHours(REPORT_RETENTION_HOURS))
                    .block();
            log.debug("Deleted {} expired Welcome Center weather report(s)", deleted);
        } catch (RuntimeException exception) {
            log.error("WelcomeCenterWeatherReportProcessor failed", exception);
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
