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
import com.kc1vmz.aprswc.constants.ObjectSymbolTableConstants;
import com.kc1vmz.aprswc.object.ObjectBeacon;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WelcomeCenterObjectBeaconProcessor {
    private static final Logger log = LoggerFactory.getLogger(WelcomeCenterObjectBeaconProcessor.class);
    private static final long BEACON_INTERVAL_MINUTES = 10;

    @Autowired
    private WelcomeCenterAccessor welcomeCenterAccessor;

    @Autowired
    private ObjectBeaconQueue objectBeaconQueue;

    private static final String STATUS_MESSAGE = "Welcome Center - %s - %s";

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            runnable -> new Thread(runnable, "WelcomeCenterObjectBeaconProcessor"));

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    void start() {
        // waiti a minute before starting to let communication threads start
        worker.scheduleWithFixedDelay(this::beaconWelcomeCenters, 1, BEACON_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    void beaconWelcomeCenters() {
        try {
            List<WelcomeCenter> welcomeCenters =
                    welcomeCenterAccessor.findAll().collectList().block();
            if (welcomeCenters == null) {
                return;
            }
            for (WelcomeCenter welcomeCenter : welcomeCenters) {
                beaconWelcomeCenterObject(welcomeCenter);
            }
        } catch (RuntimeException exception) {
            log.error("WelcomeCenterObjectBeaconProcessor failed", exception);
        }
    }

    void beaconWelcomeCenterObject(WelcomeCenter welcomeCenter) {
        if (welcomeCenter.isOpen() && (welcomeCenter.getLatitude() != null) && (welcomeCenter.getLongitude() != null)) {
            String statusMessage = String.format(
                    STATUS_MESSAGE,
                    welcomeCenter.getName(),
                    (welcomeCenter.getDescription() != null) ? welcomeCenter.getDescription() : "");
            String symbolCode = welcomeCenter.getSymbolCode();
            String symbolId = welcomeCenter.getSymbolId();

            if (symbolCode == null) {
                symbolCode = ObjectSymbolTableConstants.DEFAULT_SYMBOL_TABLE_CODE;
            }
            if (symbolId == null) {
                symbolId = ObjectSymbolTableConstants.DEFAULT_SYMBOL_TABLE_ID;
            }

            ObjectBeacon objectBeacon = new ObjectBeacon(
                    welcomeCenter.getCallsign(),
                    welcomeCenter.getOwnerCallsign(),
                    welcomeCenter.getLongitude(),
                    welcomeCenter.getLatitude(),
                    symbolCode,
                    symbolId,
                    statusMessage,
                    true);
            objectBeaconQueue.offer(objectBeacon);
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
