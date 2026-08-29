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
package com.kc1vmz.aprswc.enumeration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EnumerationsTest {
    @Test
    void validValuesRoundTrip() {
        assertAll(
                () -> assertEquals(RegionType.CIRCLE, RegionType.valueOf("CIRCLE")),
                () -> assertEquals(DistanceUnit.KILOMETERS, DistanceUnit.valueOf("KILOMETERS")),
                () -> assertEquals(StationState.STATIONARY, StationState.valueOf("STATIONARY")),
                () -> assertEquals(MessageType.BULLETIN, MessageType.valueOf("BULLETIN")),
                () -> assertEquals(PacketType.MICE, PacketType.valueOf("MICE")),
                () -> assertEquals(StationCommandType.IGNOREME, StationCommandType.valueOf("IGNOREME")),
                () -> assertEquals(CommunicationEventType.EXIT_REGION, CommunicationEventType.valueOf("EXIT_REGION")));

        assertArrayEquals(
                new PacketType[] {
                    PacketType.UNKNOWN, PacketType.MESSAGE, PacketType.MICE, PacketType.LOCATION, PacketType.WEATHER
                },
                PacketType.values());
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RegionType.valueOf("TRIANGLE"));
        assertThrows(IllegalArgumentException.class, () -> StationState.valueOf("PARKED"));
        assertThrows(IllegalArgumentException.class, () -> PacketType.valueOf("TELEMETRY"));
    }
}
