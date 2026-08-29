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
package com.kc1vmz.aprswc.parser;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPacket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocationPacketParserTest {
    @Test
    void returnsNullWhileParsingIsStubbed() {
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "location", null);
        Station station = new Station(UUID.randomUUID(), "N1ABC", StationState.UNKNOWN, List.of());

        assertNull(new LocationPacketParser().parseLocationPacket(packet, station));
    }
}
