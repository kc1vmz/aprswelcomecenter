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

import com.kc1vmz.aprswc.enumeration.PacketType;
import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.parser.MicEPacketParser;
import com.kc1vmz.aprswc.parser.PacketParser;
import com.kc1vmz.aprswc.processor.aprs.kiss.APRSKISSListenerAccessor;
import com.kc1vmz.aprswc.processor.aprs.kiss.KissPacketBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KissReceiveFramingTest {
    private static class MemoryKiss extends APRSKISSListenerAccessor {
        MemoryKiss(byte[] bytes) {
            super(new CommunicationConfig(
                    UUID.randomUUID(),
                    0,
                    "KISS_TCP",
                    "localhost",
                    8001,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
            input = new ByteArrayInputStream(bytes);
        }

        public void connect() {}

        public void close() {}
    }

    private byte[] frame(String destination, String body) {
        var ax25 = new ByteArrayOutputStream();
        address(ax25, destination, 0, false);
        address(ax25, "KC1VMZ", 6, false);
        address(ax25, "WIDE2", 1, true);
        ax25.write(3);
        ax25.write(0xf0);
        ax25.writeBytes(body.getBytes(StandardCharsets.US_ASCII));
        return KissPacketBuilder.build(ax25.toByteArray(), (byte) 0);
    }

    private void address(ByteArrayOutputStream bytes, String callsign, int ssid, boolean last) {
        for (char c : String.format("%-6s", callsign).toCharArray()) bytes.write(c << 1);
        bytes.write(0x60 | (ssid << 1) | (last ? 1 : 0));
    }

    @Test
    void returnsAlternatingPositionAndMicEPacketsWithSharedOrSeparateDelimiters() throws Exception {
        for (boolean shared : List.of(true, false)) {
            var bytes = new ByteArrayOutputStream();
            String position = "=4222.88N/07157.29W67.96V";
            String mice = "`cU9l \u001c6/13.78V  ";
            for (int n = 0; n < 4; n++) {
                byte[] next = frame(n % 2 == 0 ? "APBTUV" : "TR2R8X", n % 2 == 0 ? position : mice);
                int start = shared && n > 0 ? 1 : 0;
                bytes.write(next, start, next.length - start);
            }
            var transport = new MemoryKiss(bytes.toByteArray());
            for (int n = 0; n < 4; n++) {
                var packet = transport.read();
                assertNotNull(packet);
                assertTrue(packet.getCommand().endsWith(n % 2 == 0 ? position : mice));
                assertEquals(n % 2 == 0 ? PacketType.LOCATION : PacketType.MICE, new PacketParser().parse(packet));
                if (n % 2 != 0) {
                    var station = new Station(null, "KC1VMZ-6", StationState.UNKNOWN, List.of());
                    var decoded = new MicEPacketParser().parseMicEPacket(packet, station);
                    assertEquals("4222.88N", decoded.getLatitude());
                    assertEquals("07157.29W", decoded.getLongitude());
                }
            }
            assertThrows(EOFException.class, transport::read);
        }
    }

    @Test
    void rejectsBrokenFramesAndResynchronizesAtTheirClosingDelimiter() throws Exception {
        for (byte[] broken :
                List.of(new byte[] {0, 1, (byte) 0xdb}, new byte[] {0, (byte) 0xdb, 42, 1}, new byte[4097])) {
            var bytes = new ByteArrayOutputStream();
            bytes.write(0xc0);
            bytes.writeBytes(broken);
            bytes.writeBytes(frame("TR2R8X", "`cU9l \u001c6/13.78V"));
            var transport = new MemoryKiss(bytes.toByteArray());
            assertNotNull(transport.read());
            assertThrows(EOFException.class, transport::read);
        }
    }

    @Test
    void ignoresEmptyFramesAndKeepsEscapesFromBecomingDelimiters() throws Exception {
        var bytes = new ByteArrayOutputStream();
        bytes.writeBytes(new byte[] {12, 34, (byte) 0xc0, (byte) 0xc0});
        // Escape bytes inside a rejected control frame must not split the stream.
        bytes.writeBytes(new byte[] {1, (byte) 0xdb, (byte) 0xdc, (byte) 0xdb, (byte) 0xdd, (byte) 0xc0});
        byte[] valid = frame("TR2R8X", "`cU9l \u001c6/13.78V");
        bytes.write(valid, 1, valid.length - 1);
        var transport = new MemoryKiss(bytes.toByteArray());
        assertNull(transport.read());
        assertNotNull(transport.read());
        assertThrows(EOFException.class, transport::read);
        assertThrows(EOFException.class, new MemoryKiss(new byte[] {0, 1, 2})::read);
    }
}
