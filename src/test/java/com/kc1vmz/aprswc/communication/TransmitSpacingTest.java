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

import com.kc1vmz.aprswc.processor.StationPacketQueue;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TransmitSpacingTest {
    private ManagedPacketListener worker(String type, int spacing) throws Exception {
        var transport = mock(CommunicationTransport.class);
        var closed = new CountDownLatch(1);
        when(transport.read()).thenAnswer(call -> {
            closed.await();
            return null;
        });
        doAnswer(call -> {
                    closed.countDown();
                    return null;
                })
                .when(transport)
                .close();
        var config = new CommunicationConfig(
                UUID.randomUUID(), 0, type, null, null, null, null, null, null, null, null, null, null, spacing);
        var worker = new ManagedPacketListener(config, new StationPacketQueue(), () -> transport);
        worker.start();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (!"CONNECTED".equals(worker.health())) Thread.sleep(5);
        });
        return worker;
    }

    @Test
    void spacesRfWritesAndRechecksEligibility() throws Exception {
        var worker = worker("KISS_TCP", 150);
        var writes = new LinkedBlockingQueue<Long>();
        try {
            assertTrue(worker.send(t -> writes.add(System.nanoTime()), () -> {}));
            Long first = writes.poll(2, TimeUnit.SECONDS);
            assertNotNull(first);
            assertTrue(worker.send(t -> fail("Canceled packet written"), () -> false, () -> fail()));
            assertTrue(worker.send(t -> writes.add(System.nanoTime()), () -> {}));
            Long second = writes.poll(2, TimeUnit.SECONDS);
            assertNotNull(second);
            assertTrue(second - first >= TimeUnit.MILLISECONDS.toNanos(150));
        } finally {
            assertTrue(worker.stop());
        }
    }

    @Test
    void zeroAndInternetHaveNoSpacingAndOtherInstancesAreIndependent() throws Exception {
        var slow = worker("KISS_TCP", 10000);
        var writes = new LinkedBlockingQueue<String>();
        try {
            slow.send(t -> writes.add("first"), () -> {});
            assertEquals("first", writes.poll(2, TimeUnit.SECONDS));
            slow.send(t -> writes.add("delayed"), () -> {});
            for (String type : new String[] {"KISS_TCP", "APRS_IS"}) {
                var fast = worker(type, type.equals("APRS_IS") ? 10000 : 0);
                try {
                    fast.send(t -> writes.add("one"), () -> {});
                    fast.send(t -> writes.add("two"), () -> {});
                    assertEquals("one", writes.poll(2, TimeUnit.SECONDS));
                    assertEquals("two", writes.poll(2, TimeUnit.SECONDS));
                } finally {
                    assertTrue(fast.stop());
                }
            }
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertTrue(slow.stop()));
            assertTrue(writes.isEmpty());
        } finally {
            slow.stop();
        }
    }
}
