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
import com.kc1vmz.aprswc.processor.aprs.is.*;
import com.kc1vmz.aprswc.processor.aprs.kiss.*;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class CommunicationTransportTest {
    private CommunicationConfig config(String type, int port) {
        return new CommunicationConfig(
                UUID.randomUUID(),
                0,
                type,
                "127.0.0.1",
                port,
                "N1TEST",
                "123",
                "m/10",
                null,
                null,
                null,
                null,
                "WIDE1-1");
    }

    @Test
    void internetConnectionsHaveIndependentConfigurationAndSourceIdsAndCanSendDuringRead() throws Exception {
        try (var first = new ServerSocket(0);
                var second = new ServerSocket(0)) {
            first.setSoTimeout(3000);
            second.setSoTimeout(3000);
            var utility = mock(APRSUtilityAccessor.class);
            when(utility.generateAuthStr(any(), any())).thenReturn("user N1TEST pass 123");
            var a = config("APRS_IS", first.getLocalPort());
            var b = config("APRS_IS", second.getLocalPort());
            var t1 = new APRSInternetServerListenerAccessor(a, utility);
            var t2 = new APRSInternetServerListenerAccessor(b, utility);
            try {
                t1.connect();
                t2.connect();
                try (var peer1 = first.accept();
                        var peer2 = second.accept()) {
                    peer1.setSoTimeout(3000);
                    peer2.setSoTimeout(3000);
                    var in1 = new BufferedReader(new InputStreamReader(peer1.getInputStream()));
                    var in2 = new BufferedReader(new InputStreamReader(peer2.getInputStream()));
                    assertEquals("user N1TEST pass 123", in1.readLine());
                    assertEquals("user N1TEST pass 123", in2.readLine());
                    var reading = CompletableFuture.supplyAsync(() -> {
                        try {
                            return t1.read();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    t1.sendMessage("N1TEST", "N2TEST", "Hello");
                    assertTrue(in1.readLine().endsWith("::N2TEST   :Hello"));
                    peer1.getOutputStream().write("N2TEST>APRS:one\r\n".getBytes());
                    peer2.getOutputStream().write("N3TEST>APRS:two\r\n".getBytes());
                    assertEquals(
                            a.id().toString(), reading.get(3, TimeUnit.SECONDS).getPacketProcessorId());
                    assertEquals(b.id().toString(), t2.read().getPacketProcessorId());
                }
            } finally {
                t1.close();
                t2.close();
            }
        }
    }

    @Test
    void kissTcpHandlesSplitAndCoalescedFramesAndStampsItsUuid() throws Exception {
        try (var server = new ServerSocket(0)) {
            var c = config("KISS_TCP", server.getLocalPort());
            var transport = new APRSTCPIPListenerAccessor(c);
            try {
                transport.connect();
                try (var peer = server.accept()) {
                    var packet = new KISSPacket();
                    packet.setCallsignFrom("N1TEST");
                    packet.setCallsignTo("N2TEST");
                    packet.setApplicationName("APRS");
                    packet.setDigipeaters(List.of("WIDE1-1"));
                    packet.setData("hello");
                    byte[] frame = KissPacketBuilder.build(AX25PacketBuilder.buildPacket(packet, ":"), (byte) 0);
                    var out = peer.getOutputStream();
                    out.write(frame, 0, 5);
                    out.flush();
                    out.write(frame, 5, frame.length - 5);
                    out.write(frame);
                    out.flush();
                    var first = transport.read();
                    var second = transport.read();
                    assertEquals(c.id().toString(), first.getPacketProcessorId());
                    assertEquals("N1TEST", first.getCallsign());
                    assertEquals(first.getCommand(), second.getCommand());
                    assertTrue(first.getCommand().contains("WIDE1-1"));
                }
            } finally {
                transport.close();
            }
        }
    }

    @Test
    void stopClosesBlockedReadAndDisablesRouting() throws Exception {
        try (var server = new ServerSocket(0)) {
            var worker = new PacketListenerKISS(config("KISS_TCP", server.getLocalPort()), new StationPacketQueue());
            worker.start();
            try (var peer = server.accept()) {
                assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                    while (!worker.health().equals("CONNECTED")) Thread.sleep(10);
                });
                assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertTrue(worker.stop()));
                assertFalse(
                        worker.send(t -> t.sendMessage("A", "B", "C"), () -> fail("Stopped sends must be dropped")));
                assertEquals(-1, peer.getInputStream().read());
            } finally {
                worker.stop();
            }
        }
    }
}
