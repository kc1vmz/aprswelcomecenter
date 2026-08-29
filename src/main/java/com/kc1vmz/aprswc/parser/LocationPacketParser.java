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
import com.kc1vmz.aprswc.utils.CompressedDataFormatUtils;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LocationPacketParser {
    private static final int LENGTH_BYTES = 36;

    public StationPosition parseLocationPacket(StationPacket packet, Station station) {

        if ((packet == null) || (station == null) || (packet.getCommand() == null)) {
            return null;
        }

        String content = packet.getCommand();
        byte[] data = content.getBytes();
        char dti = content.charAt(0);
        if ((dti != '!') && (dti != '=') && (dti != '/') && (dti != '@')) {
            return null;
        }

        int messageIndex = 0;
        String lat = null;
        String lon = null;
        StationPosition stationPosition = null;

        messageIndex += 1; // move down buffer
        if ((dti == '!') || (dti == '=')) {
            // no timestamp
        } else {
            messageIndex += 7; // account for timestamp
        }

        if ((data[messageIndex] < '0') || (data[messageIndex] > '9')) { // if it is not a digit, then it is compressed
            // compressed location data
            byte[] compressedData = Arrays.copyOfRange(data, messageIndex, messageIndex + 13);
            lon = CompressedDataFormatUtils.convertDDMMSSxToAPRSDDMMSSx(
                    CompressedDataFormatUtils.convertDecimalToDDDMMSSx(
                            CompressedDataFormatUtils.getLongitude(compressedData), "EW"));
            lat = CompressedDataFormatUtils.convertDDMMSSxToAPRSDDMMSSx(
                    CompressedDataFormatUtils.convertDecimalToDDMMSSx(
                            CompressedDataFormatUtils.getLatitude(compressedData), "NS"));

            messageIndex += 13; // compressed is 13 bytes
        } else {
            byte[] latByte = Arrays.copyOfRange(data, messageIndex, messageIndex + 8);
            messageIndex += 9;
            byte[] lonByte = Arrays.copyOfRange(data, messageIndex, messageIndex + 9);
            if ((lonByte[8] != 'E') && (lonByte[8] != 'W')) {
                lonByte = Arrays.copyOfRange(data, messageIndex + 0, messageIndex + 8);
                String prependZero = "0" + new String(lonByte);
                lonByte = prependZero.getBytes();
                messageIndex += 8;
                // bad lon - fixed up
            } else {
                messageIndex += 9;
                // good lon
            }
            messageIndex++;
            lat = new String(latByte);
            lon = new String(lonByte);
        }

        if ((lat != null) && (lon != null)) {
            stationPosition =
                    new StationPosition(UUID.randomUUID(), station.getCallsign(), lon, lat, LocalDateTime.now());
        }

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
}
