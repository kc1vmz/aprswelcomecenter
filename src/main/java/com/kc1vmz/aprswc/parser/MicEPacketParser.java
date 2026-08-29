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

import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.utils.MicELatDigitProcessor;
import com.kc1vmz.aprswc.utils.MicELonDegreeProcessor;
import com.kc1vmz.aprswc.utils.MicELonMinuteProcessor;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MicEPacketParser {
    private static final int LENGTH_BYTES = 36;

    public StationPosition parseMicEPacket(StationPacket packet, Station station) {

        if ((packet == null) || (packet.getCommand() == null)) {
            return null;
        }

        String content = packet.getCommand();
        byte[] lonBytes = content.substring(1, 4).getBytes();

        String destField = packet.getCallsignTo();
        if (destField == null) {
            return null;
        }

        String lat = determineLatitude(destField);
        String lon = determineLongitude(destField, lonBytes, (lat.contains("  ")));

        StationPosition stationPosition =
                new StationPosition(UUID.randomUUID(), station.getCallsign(), lon, lat, LocalDateTime.now());

        return stationPosition;
    }

    public static String getCallsignFrom(byte[] data) throws NullPointerException {
        return getCallsign(data, 8);
    }

    public static String getCallsignTo(byte[] data) throws NullPointerException {
        return getCallsign(data, 18);
    }

    private static String getCallsign(byte[] data, int offset) {
        if (data == null) {
            return null;
        }
        if (data.length < LENGTH_BYTES) {
            return null;
        }

        String callsign = "";
        for (int i = 0; i < 10; i++) {
            if (data[offset + i] != 0) {
                callsign += (char) data[offset + i];
            }
        }
        return callsign;
    }

    private static String determineLongitude(String destField, byte[] lonBytes, boolean isAmbiguous) {
        boolean longOffset = determineLongOffset(destField);
        if ((destField == null) || (destField.length() < 6)) {
            return "";
        }
        if ((lonBytes == null) || (lonBytes.length != 3)) {
            return "";
        }
        MicELatDigitProcessor d5 = new MicELatDigitProcessor(destField.charAt(5));
        MicELatDigitProcessor d4 = new MicELatDigitProcessor(destField.charAt(4));

        MicELonDegreeProcessor degrees = new MicELonDegreeProcessor(lonBytes[0], longOffset);
        int degreesAdjusted = degrees.getValue();
        if (d4.getLongOffset() == 100) {
            degreesAdjusted -= 100;
        }
        MicELonMinuteProcessor minutes = new MicELonMinuteProcessor(lonBytes[1]);
        String ret = "";
        if (isAmbiguous) {
            ret = String.format("%03d%02d.  %s", degreesAdjusted, minutes.getValue(), ((d5.isWest() ? "W" : "E")));
        } else {
            ret = String.format(
                    "%03d%02d.%02d%s",
                    degreesAdjusted, minutes.getValue(), lonBytes[2] - 28, ((d5.isWest() ? "W" : "E")));
        }
        return ret;
    }

    private static boolean determineLongOffset(String destField) {
        MicELatDigitProcessor d1 = new MicELatDigitProcessor(destField.charAt(4));
        return (d1.getLongOffset() == 100);
    }

    private static String determineLatitude(String destField) {
        String lat = "";
        if ((destField == null) || (destField.length() < 6)) {
            return lat;
        }

        MicELatDigitProcessor d1 = new MicELatDigitProcessor(destField.charAt(0));
        MicELatDigitProcessor d2 = new MicELatDigitProcessor(destField.charAt(1));
        MicELatDigitProcessor d3 = new MicELatDigitProcessor(destField.charAt(2));
        MicELatDigitProcessor d4 = new MicELatDigitProcessor(destField.charAt(3));

        if (destField.endsWith("ZZ")) {
            lat = d1.getDigit() + d2.getDigit() + d3.getDigit() + d4.getDigit() + ".  " + ((d1.isNorth() ? "N" : "S"));
            lat = String.format(
                    "%c%c%c%c.  %s",
                    d1.getDigit(), d2.getDigit(), d3.getDigit(), d4.getDigit(), (d4.isNorth() ? "N" : "S"));
        } else {
            MicELatDigitProcessor d5 = new MicELatDigitProcessor(destField.charAt(4));
            MicELatDigitProcessor d6 = new MicELatDigitProcessor(destField.charAt(5));
            lat = String.format(
                    "%c%c%c%c.%c%c%s",
                    d1.getDigit(),
                    d2.getDigit(),
                    d3.getDigit(),
                    d4.getDigit(),
                    d5.getDigit(),
                    d6.getDigit(),
                    (d4.isNorth() ? "N" : "S"));
        }
        return lat;
    }
}
