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

import static org.junit.jupiter.api.Assertions.*;

import com.kc1vmz.aprswc.enumeration.PacketType;
import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.utils.ConvertLonLat;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeatherPositionRegressionTest {
    @Test
    void shortUncompressedPositionUsesActualCoordinatesInsteadOfWeatherCoordinates() {
        for (String suffix : List.of("", "  ", " longer comment")) {
            var packet = new StationPacket(
                    null,
                    "test",
                    null,
                    null,
                    "KC1VMZ-6>APBTUV,WIDE1-1,WIDE2-1,qAR,W1DMR-3:=4222.88N/07157.29W67.96V" + suffix,
                    null);
            assertEquals(PacketType.LOCATION, new PacketParser().parse(packet));
            assertNull(new WeatherPacketParser().parseWeatherPacket(packet));
            var station = new Station(null, "KC1VMZ-6", StationState.UNKNOWN, List.of());
            var position = new LocationPacketParser().parseLocationPacket(packet, station);
            assertEquals("4222.88N", position.getLatitude());
            assertEquals("07157.29W", position.getLongitude());
            assertEquals(42.381333333, ConvertLonLat.convertLatitude(position.getLatitude()), 0.000000001);
            assertEquals(-71.954833333, ConvertLonLat.convertLongitude(position.getLongitude()), 0.000000001);
        }
    }

    @Test
    void weatherDetectionUsesSymbolForBothTimestampedAndUntimestampedCompressedPositions() {
        for (String prefix : List.of("=", "!", "@221200z", "/221200z")) {
            for (String suffix : List.of("t070", "t070h50b10130 long comment")) {
                var weather = new StationPacket(null, "test", "N1ABC", null, prefix + "/55555555_!!!" + suffix, null);
                var report = new WeatherPacketParser().parseWeatherPacket(weather);
                assertNotNull(report);
                assertEquals(70f, report.getTemperature());
                var ordinary = new StationPacket(null, "test", "N1ABC", null, prefix + "/55555555>!!!" + suffix, null);
                assertNull(new WeatherPacketParser().parseWeatherPacket(ordinary));
            }
        }
    }

    @Test
    void ordinaryUncompressedWeatherStillParses() {
        var packet = new StationPacket(null, "test", "N1ABC", null, "=4222.88N/07157.29W_000/000t070h50", null);
        var report = new WeatherPacketParser().parseWeatherPacket(packet);
        assertNotNull(report);
        assertEquals("4222.88N", report.getLatitude());
        assertEquals("07157.29W", report.getLongitude());
        assertEquals(70f, report.getTemperature());
    }
}
