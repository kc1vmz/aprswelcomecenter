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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.database.CommunicationInstanceRepository;
import com.kc1vmz.aprswc.object.CommunicationInstance;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import com.kc1vmz.aprswc.processor.aprs.is.APRSUtilityAccessor;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CommunicationInstanceManagerTest {
    private CommunicationInstance config(int port) {
        var c = new CommunicationInstance();
        c.setId(UUID.randomUUID());
        c.setType("KISS_TCP");
        c.setState("ACTIVE");
        c.setHost("127.0.0.1");
        c.setPort(port);
        return c;
    }

    private void connected(CommunicationInstanceManager manager, UUID id) {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (!manager.health(id).status().equals("CONNECTED")) Thread.sleep(10);
        });
    }

    @Test
    void startsMultipleInstancesFansOutAndStopsBeforeRestartPauseAndDelete() throws Exception {
        try (var server1 = new ServerSocket(0);
                var server2 = new ServerSocket(0)) {
            server1.setSoTimeout(3000);
            server2.setSoTimeout(3000);
            var first = config(server1.getLocalPort());
            var second = config(server2.getLocalPort());
            var desired = new AtomicReference<>(List.of(first, second));
            var repository = mock(CommunicationInstanceRepository.class);
            when(repository.findAll()).thenAnswer(i -> desired.get());
            var manager = new CommunicationInstanceManager(
                    repository, new StationPacketQueue(), mock(APRSUtilityAccessor.class));
            try {
                manager.start();
                try (var peer1 = server1.accept();
                        var peer2 = server2.accept()) {
                    peer1.setSoTimeout(3000);
                    peer2.setSoTimeout(3000);
                    connected(manager, first.getId());
                    connected(manager, second.getId());
                    manager.sendBulletin("N1TEST", "BLN1", "hello");
                    assertEquals(0xc0, peer1.getInputStream().read());
                    assertEquals(0xc0, peer2.getInputStream().read());
                    // Drain each complete frame before checking EOF on the stopped connection.
                    while (peer1.getInputStream().read() != 0xc0) {}
                    while (peer2.getInputStream().read() != 0xc0) {}
                    first.setVersion(1);
                    manager.reconcile();
                    assertEquals(-1, peer1.getInputStream().read());
                    try (var replacement = server1.accept()) {
                        replacement.setSoTimeout(3000);
                        connected(manager, first.getId());
                        first.setState("PAUSED");
                        first.setVersion(2);
                        manager.reconcile();
                        assertEquals(-1, replacement.getInputStream().read());
                        assertFalse(manager.sendMessage(
                                first.getId().toString(), "N1TEST", "N2TEST", "drop", () -> fail()));
                        assertEquals("CONNECTED", manager.health(second.getId()).status());
                        desired.set(List.of(first));
                        manager.reconcile();
                        assertEquals(-1, peer2.getInputStream().read());
                        assertFalse(manager.sendMessage(
                                second.getId().toString(), "N1TEST", "N2TEST", "drop", () -> fail()));
                    }
                }
            } finally {
                manager.stop();
            }
        }
    }
}
