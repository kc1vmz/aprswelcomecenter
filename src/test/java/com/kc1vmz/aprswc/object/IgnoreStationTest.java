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
package com.kc1vmz.aprswc.object;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IgnoreStationTest {
    @Test
    void constructorAndAccessorsExposeFields() {
        UUID id = UUID.randomUUID();
        IgnoreStation station = new IgnoreStation(id, "KC1VMZ");
        assertEquals(id, station.getId());
        assertEquals("KC1VMZ", station.getCallsign());

        UUID replacementId = UUID.randomUUID();
        station.setId(replacementId);
        station.setCallsign("N1ABC");
        assertEquals(replacementId, station.getId());
        assertEquals("N1ABC", station.getCallsign());
    }
}
