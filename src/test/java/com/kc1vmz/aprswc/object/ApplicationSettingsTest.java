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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationSettingsTest {
    @Test
    void constructorAndAccessorsExposeAllFields() {
        UUID id = UUID.randomUUID();
        ApplicationSettings settings = new ApplicationSettings(
                id,
                true,
                false,
                "rotate.aprs2.net",
                "KC1VMZ",
                "12345",
                "14580",
                "localhost",
                "8001",
                "9600",
                "KISS ON",
                "RESTART",
                "WIDE1-1,WIDE2-1",
                "r/42.3/-71.1/50",
                "https://tiles.example.test/{z}/{x}/{y}.png");

        assertAll(
                () -> assertEquals(id, settings.getId()),
                () -> assertTrue(settings.isUsingInternetServer()),
                () -> assertFalse(settings.isUsingKISS()),
                () -> assertEquals("rotate.aprs2.net", settings.getInternetServerAddress()),
                () -> assertEquals("KC1VMZ", settings.getInternetServerUsername()),
                () -> assertEquals("12345", settings.getInternetServerPasscode()),
                () -> assertEquals("14580", settings.getInternetServerPort()),
                () -> assertEquals("localhost", settings.getKissHost()),
                () -> assertEquals("8001", settings.getKissPort()),
                () -> assertEquals("9600", settings.getKissBaudRate()),
                () -> assertEquals("KISS ON", settings.getKissInitCommand1()),
                () -> assertEquals("RESTART", settings.getKissInitCommand2()),
                () -> assertEquals("WIDE1-1,WIDE2-1", settings.getDigiPath()),
                () -> assertEquals("r/42.3/-71.1/50", settings.getFilter()),
                () -> assertEquals("https://tiles.example.test/{z}/{x}/{y}.png", settings.getMapTileUrl()));

        settings.setUsingInternetServer(false);
        settings.setUsingKISS(true);
        settings.setKissInitCommand1(null);
        settings.setKissInitCommand2("RESET");
        settings.setFilter("m/25");
        settings.setMapTileUrl(null);
        assertAll(
                () -> assertFalse(settings.isUsingInternetServer()),
                () -> assertTrue(settings.isUsingKISS()),
                () -> assertEquals(null, settings.getKissInitCommand1()),
                () -> assertEquals("RESET", settings.getKissInitCommand2()),
                () -> assertEquals("m/25", settings.getFilter()),
                () -> assertEquals(null, settings.getMapTileUrl()));
    }
}
