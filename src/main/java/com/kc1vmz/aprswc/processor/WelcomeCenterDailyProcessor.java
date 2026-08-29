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

import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WelcomeCenterDailyProcessor {
    private static final Logger log = LoggerFactory.getLogger(WelcomeCenterDailyProcessor.class);
    private static final long PROCESSING_INTERVAL_HOURS = 24;

    @Autowired
    private WelcomeCenterAccessor welcomeCenterAccessor;

    @Autowired
    private StationMessageQueue stationMessageQueue;

    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "WelcomeCenterDailyProcessor"));

    @PostConstruct
    void start() {
        worker.scheduleWithFixedDelay(this::processWelcomeCenters, 0, PROCESSING_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    void processWelcomeCenters() {
        try {
            List<WelcomeCenter> welcomeCenters =
                    welcomeCenterAccessor.findAll().collectList().block();
            if (welcomeCenters == null) {
                return;
            }
            for (WelcomeCenter welcomeCenter : welcomeCenters) {
                processWelcomeCenter(welcomeCenter);
            }
        } catch (RuntimeException exception) {
            log.error("WelcomeCenterDailyProcessor failed", exception);
        }
    }

    void processWelcomeCenter(WelcomeCenter welcomeCenter) {
        String bulletinContent = String.format(
                "APRS Welcome Center %s open - send HELP for more commands.", welcomeCenter.getCallsign());
        StationMessage stationMessage = new StationMessage(
                UUID.randomUUID(),
                "BLN0",
                welcomeCenter.getCallsign(),
                welcomeCenter,
                LocalDateTime.now(),
                bulletinContent,
                null,
                MessageType.BULLETIN);
        stationMessageQueue.offer(stationMessage);
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
