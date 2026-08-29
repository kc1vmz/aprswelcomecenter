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

import com.kc1vmz.aprswc.accessor.ApplicationSettingsAccessor;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import com.kc1vmz.aprswc.object.ObjectBeacon;
import com.kc1vmz.aprswc.processor.aprs.is.APRSInternetServerListenerAccessor;
import com.kc1vmz.aprswc.processor.aprs.kiss.APRSKISSListenerAccessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ObjectBeaconProcessor {
    private static final Logger log = LoggerFactory.getLogger(ObjectBeaconProcessor.class);

    @Autowired
    private ObjectBeaconQueue queue;

    @Autowired
    private APRSInternetServerListenerAccessor aprsInternetServerListenerAccessor;

    @Autowired
    private APRSKISSListenerAccessor aprsKISSListenerAccessor;

    @Autowired
    private ApplicationSettingsAccessor applicationSettingsAccessor;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "ObjectBeaconProcessor"));

    @PostConstruct
    void start() {
        worker.submit(this::process);
    }

    private void process() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ObjectBeacon value = queue.take();
                processObjectBeacon(value);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                log.error("ObjectBeaconProcessor failed", exception);
            }
        }
    }

    void processObjectBeacon(ObjectBeacon objectBeacon) {
        ApplicationSettings applicationSettings =
                applicationSettingsAccessor.findAll().next().block();
        if (applicationSettings == null) {
            log.warn("ObjectBeacon cannot be sent because application settings are not configured");
            return;
        }

        if (applicationSettings.isUsingInternetServer()) {
            aprsInternetServerListenerAccessor.sendObject(
                    objectBeacon.getObjectName(),
                    objectBeacon.getStatusMessage(),
                    objectBeacon.isActive(),
                    objectBeacon.getLatitude(),
                    objectBeacon.getLongitude(),
                    objectBeacon.getSymbolId(),
                    objectBeacon.getSymbolCode());
        }
        if (applicationSettings.isUsingKISS()) {
            aprsKISSListenerAccessor.sendObject(
                    objectBeacon.getObjectName(),
                    objectBeacon.getStatusMessage(),
                    objectBeacon.isActive(),
                    objectBeacon.getLatitude(),
                    objectBeacon.getLongitude(),
                    objectBeacon.getSymbolId(),
                    objectBeacon.getSymbolCode());
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
