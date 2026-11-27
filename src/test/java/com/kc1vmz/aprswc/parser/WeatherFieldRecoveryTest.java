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

import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherReport;
import org.junit.jupiter.api.Test;

class WeatherFieldRecoveryTest {
    private WelcomeCenterWeatherReport parse(String packet) {
        int colon = packet.indexOf(':');
        return new WeatherPacketParser()
                .parseWeatherPacket(new StationPacket(
                        null,
                        "test",
                        packet.substring(0, packet.indexOf('>')),
                        null,
                        packet.substring(colon + 1),
                        null));
    }

    @Test
    void shortPressurePreservesMeasurementsAndDoesNotReadSoftwareSuffix() {
        var report = parse(
                "N1HRK>APU25N,WIDE2-2,qAR,N1HRK-15:@271628z4133.95N/07129.60W_219/005g011t059r000P001p205h70b9998wU2100 {UIV32N}t999L999");
        assertEquals(59f, report.getTemperature());
        assertEquals(70f, report.getHumidity());
        assertEquals("4133.95N", report.getLatitude());
        assertEquals("07129.60W", report.getLongitude());
        assertNull(report.getBarometricPressure());
        assertNull(report.getLuminosity());
    }

    @Test
    void malformedRainDoesNotDiscardLaterValidMeasurements() {
        var report = parse(
                "KC1TLF-15>APRS,TCPIP*,qAC,T2USANW:_11270122c330s001g001t040r000P14.000000000000002h99b10163L000wNA");
        assertEquals(40f, report.getTemperature());
        assertEquals(99f, report.getHumidity());
        assertEquals(10163f, report.getBarometricPressure());
        assertEquals(0f, report.getLuminosity());
    }

    @Test
    void invalidPressureRecoversAtNextFieldAndValidPressureIsUnchanged() {
        assertEquals(9998f, parse("WX>APRS:_11270122t059b09998wU2100").getBarometricPressure());
        var report = parse("WX>APRS:_11270122t059b9998h70L012");
        assertNull(report.getBarometricPressure());
        assertEquals(70f, report.getHumidity());
        assertEquals(12f, report.getLuminosity());
    }

    @Test
    void missingValuesRemainNullAndNegativeTemperatureAndHumidityZeroAreHandled() {
        var report = parse("WX>APRS:_11270122c...s...g...t-05r000h00b.....L...");
        assertEquals(-5f, report.getTemperature());
        assertEquals(100f, report.getHumidity());
        assertNull(report.getBarometricPressure());
        assertNull(report.getLuminosity());
        assertNull(parse("WX>APRS:_11270122t   h  b     ").getTemperature());
    }

    @Test
    void unknownCommentsAndRepeatedFieldsEndMeasurements() {
        var report = parse("WX>APRS:_11270122t040h99 comment b10163L999");
        assertNull(report.getBarometricPressure());
        assertNull(report.getLuminosity());
        assertEquals(40f, parse("WX>APRS:_11270122t040t999").getTemperature());
        assertNull(parse("WX>APRS:_11270122t040b1").getBarometricPressure());
    }
}
