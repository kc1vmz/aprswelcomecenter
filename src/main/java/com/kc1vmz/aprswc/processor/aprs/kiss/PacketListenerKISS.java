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
package com.kc1vmz.aprswc.processor.aprs.kiss;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PacketListenerKISS {
    private static final Logger log = LoggerFactory.getLogger(PacketListenerKISS.class);
    private static final long IDLE_WAIT_NANOS = 1_000_000_000L;

    @Autowired
    private APRSKISSListenerAccessor aprsKISSListenerAccessor;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "PacketListenerKISS"));

    @PostConstruct
    void start() {
        worker.submit(this::runListener);
    }

    private void runListener() {
        try {
            aprsKISSListenerAccessor.connectAndListen();
        } catch (InterruptedException e) {
            log.warn("Exception caught", e);
        } catch (Exception e) {
            log.error("Exception caught", e);
        }

        while (!Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(IDLE_WAIT_NANOS);
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
