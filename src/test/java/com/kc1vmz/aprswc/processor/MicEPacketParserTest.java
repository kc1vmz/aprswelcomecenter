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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kc1vmz.aprswc.enumeration.PacketType;
import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.parser.MicEPacketParser;
import com.kc1vmz.aprswc.parser.PacketParser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MicEPacketParserTest {
    @Test
    void decodesBacktickMicEPacketCoordinates() {
        String rawPacket =
                "KB5LNC-12>3X2Q8U,WIDE1-1,WIDE2-2:`i<el \u001cK\\]\"4d}Greetings Earthlings,Time For Dinner=";
        StationPacket packet = new StationPacket(UUID.randomUUID(), "processor-1", "KB5LNC-12", null, rawPacket, null);
        Station station = new Station(UUID.randomUUID(), "KB5LNC-12", StationState.UNKNOWN, List.of());

        assertEquals(PacketType.MICE, new PacketParser().parse(packet));
        assertEquals("3X2Q8U", packet.getCallsignTo());
        StationPosition position = new MicEPacketParser().parseMicEPacket(packet, station);

        assertNotNull(position);
        assertEquals("3821.85N", position.getLatitude());
        assertEquals("07732.73W", position.getLongitude());
    }

    @Test
    void returnsNullForPacketWithoutCommand() {
        StationPacket packet = new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", null, null, null);
        Station station = new Station(UUID.randomUUID(), "N1ABC", StationState.UNKNOWN, List.of());

        assertNull(new MicEPacketParser().parseMicEPacket(packet, station));
    }
}
