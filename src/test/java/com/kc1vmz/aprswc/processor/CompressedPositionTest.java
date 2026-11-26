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

import static org.junit.jupiter.api.Assertions.*;

import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.parser.LocationPacketParser;
import com.kc1vmz.aprswc.parser.WeatherPacketParser;
import com.kc1vmz.aprswc.utils.AprsCoordinateFormat;
import com.kc1vmz.aprswc.utils.ConvertLonLat;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CompressedPositionTest {
    @Test
    void exportedPacketProducesUsableCoordinates() {
        var packet = new StationPacket(
                null, null, "KF8HRS", null, "!/9-_]9/fp-  CGraywolf/0.14.14 with XARA on 7083.5", null);
        var position = new LocationPacketParser().parseLocationPacket(packet, new Station(null, "KF8HRS", null, null));
        assertEquals("4214.75N", position.getLatitude());
        assertEquals("08424.08W", position.getLongitude());
        assertEquals(42.245901, ConvertLonLat.convertLatitude(position.getLatitude()), 0.0001);
        assertEquals(-84.401401, ConvertLonLat.convertLongitude(position.getLongitude()), 0.0001);
    }

    @Test
    void compressedWeatherUsesDecimalMinutesWithAndWithoutTimestamp() {
        for (String prefix : new String[] {"!", "@252043z"}) {
            var packet = new StationPacket(null, null, "KF8HRS", null, prefix + "/9-_]9/fp_  Ct070h50b10132", null);
            var report = new WeatherPacketParser().parseWeatherPacket(packet);
            assertNotNull(report);
            assertEquals("4214.75N", report.getLatitude());
            assertEquals("08424.08W", report.getLongitude());
        }
    }

    @Test
    void formattingPreservesPrecisionPaddingHemisphereAndRolloverRegardlessOfLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals("4200.08N", AprsCoordinateFormat.latitude(42.001302090169744));
            assertEquals("4200.08S", AprsCoordinateFormat.latitude(-42.001302090169744));
            assertEquals("08424.08E", AprsCoordinateFormat.longitude(84.4014007970052));
            assertEquals("4300.00N", AprsCoordinateFormat.latitude(42.999999));
            assertEquals("08500.00W", AprsCoordinateFormat.longitude(-84.999999));
            assertEquals("0000.00N", AprsCoordinateFormat.latitude(0));
            assertEquals("18000.00E", AprsCoordinateFormat.longitude(180));
            assertNull(AprsCoordinateFormat.latitude(91));
            assertNull(AprsCoordinateFormat.longitude(Double.NaN));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
