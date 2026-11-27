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

import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherReport;
import com.kc1vmz.aprswc.utils.AprsCoordinateFormat;
import com.kc1vmz.aprswc.utils.CompressedDataFormatUtils;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WeatherPacketParser {
    private static final Logger objectLog = LoggerFactory.getLogger("WeatherPacketParser");
    public static final char WEATHERREPORT_FIELD_WIND_DIRECTION = 'c';
    public static final char WEATHERREPORT_FIELD_WIND_SPEED = 's';
    public static final char WEATHERREPORT_FIELD_WIND_GUST = 'g';
    public static final char WEATHERREPORT_FIELD_TEMPERATURE = 't';
    public static final char WEATHERREPORT_FIELD_RAIN_LAST_HOUR = 'r';
    public static final char WEATHERREPORT_FIELD_RAIN_LAST_24HOUR = 'p';
    public static final char WEATHERREPORT_FIELD_RAIN_SINCE_MIDNIGHT = 'P';
    public static final char WEATHERREPORT_FIELD_HUMIDITY = 'h';
    public static final char WEATHERREPORT_FIELD_BAROMETRIC_PRESSURE = 'b';
    public static final char WEATHERREPORT_FIELD_LUMOSITY = 'L';
    public static final char WEATHERREPORT_FIELD_LUMOSITY_LARGE = 'l';
    public static final char WEATHERREPORT_FIELD_RAIN_RAW = '#';

    public static final int INDEX_WEATHERREPORT_FIELD_WIND_DIRECTION = 0;
    public static final int INDEX_WEATHERREPORT_FIELD_WIND_SPEED = 1;
    public static final int INDEX_WEATHERREPORT_FIELD_WIND_GUST = 2;
    public static final int INDEX_WEATHERREPORT_FIELD_TEMPERATURE = 3;
    public static final int INDEX_WEATHERREPORT_FIELD_RAIN_LAST_HOUR = 4;
    public static final int INDEX_WEATHERREPORT_FIELD_RAIN_LAST_24HOUR = 5;
    public static final int INDEX_WEATHERREPORT_FIELD_RAIN_SINCE_MIDNIGHT = 6;
    public static final int INDEX_WEATHERREPORT_FIELD_HUMIDITY = 7;
    public static final int INDEX_WEATHERREPORT_FIELD_BAROMETRIC_PRESSURE = 8;
    public static final int INDEX_WEATHERREPORT_FIELD_LUMOSITY = 9;
    public static final int INDEX_WEATHERREPORT_FIELD_LUMOSITY_LARGE = 10;
    public static final int INDEX_WEATHERREPORT_FIELD_RAIN_RAW = 11;

    public static final int INDEX_WEATHERREPORT_MAX = 11;

    public static final String WEATHERREPORT_ULTW = "ULTW";

    public static boolean[] createBitmask() {
        return new boolean[INDEX_WEATHERREPORT_MAX + 1];
    }

    public WelcomeCenterWeatherReport parseWeatherPacket(StationPacket packet) {
        WelcomeCenterWeatherReport ret = null;

        if ((packet == null)
                || (packet.getCommand() == null)
                || packet.getCommand().isEmpty()) {
            return ret;
        }
        char weatherPacketType = packet.getCommand().charAt(0);
        switch (weatherPacketType) {
            case '_':
                ret = parsePositionless(packet);
                break;
            case '!':
            case '=':
                ret = parseCompleteWithoutTimestamp(packet);
                break;
            case '/':
            case '@':
                ret = parseCompleteWithTimestamp(packet);
                break;
            default:
                break;
        }

        return ret;
    }

    private WelcomeCenterWeatherReport parseCompleteWithTimestamp(StationPacket packet) {
        WelcomeCenterWeatherReport ret = null;

        String content = packet.getCommand();
        if (content.length() <= 8) return null;
        if (isCompressedPosition(content, 8)) {
            return isCompressedWeatherPosition(content, 8)
                    ? parseCompleteWithTimestampCompressedLocation(packet)
                    : null;
        }
        if (content.length() < 26) return null;

        int messageIndex = 0;

        try {
            String lat = content.substring(8, 16);
            String lon = content.substring(17, 26);

            if ((!lon.endsWith("E")) && (!lon.endsWith("W"))) {
                lon = "0" + content.substring(17, 25);
                // bad lon - short one character
                messageIndex = 25;
            } else {
                messageIndex = 26;
            }

            if (content.length() < messageIndex + 8
                    || content.charAt(messageIndex) != '_'
                    || content.charAt(messageIndex + 4) != '/') {
                // this must be an underscore and have wind speed / direction separator
                return null;
            }
            messageIndex++;
            messageIndex += 7; // skip wind speed and direction

            String weatherData = content.substring(messageIndex);
            ret = parseWeatherData(packet.getCallsign(), weatherData);
            if (ret != null) {
                ret.setLatitude(lat);
                ret.setLongitude(lon);
            }
        } catch (Exception e) {
            objectLog.error(
                    String.format(
                            "Exception caught parsing weather packet - %s : %s",
                            packet.getCallsign(), packet.getCommand()),
                    e);
        }

        return ret;
    }

    private WelcomeCenterWeatherReport parseCompleteWithTimestampCompressedLocation(StationPacket packet) {
        WelcomeCenterWeatherReport ret = null;
        // compressed location data
        try {
            String content = packet.getCommand();
            String compressedDataStr = content.substring(9, 17);
            byte[] compressedData = compressedDataStr.getBytes();
            String lat = AprsCoordinateFormat.latitude(CompressedDataFormatUtils.getLatitudeShort(compressedData));
            String lon = AprsCoordinateFormat.longitude(CompressedDataFormatUtils.getLongitudeShort(compressedData));

            String weatherData = content.substring(21);
            ret = parseWeatherData(packet.getCallsign(), weatherData);
            if (ret != null) {
                ret.setLatitude(lat);
                ret.setLongitude(lon);
            }
        } catch (Exception e) {
            objectLog.error(
                    String.format(
                            "Exception caught parsing weather packet - %s : %s",
                            packet.getCallsign(), packet.getCommand()),
                    e);
        }

        return ret;
    }

    private WelcomeCenterWeatherReport parseCompleteWithoutTimestampCompressedLocation(StationPacket packet) {
        WelcomeCenterWeatherReport ret = null;

        String content = packet.getCommand();
        if ((content == null) || (content.isBlank()) || (content.isEmpty())) {
            return ret;
        }

        try {
            String compressedDataStr = content.substring(2, 10);
            byte[] compressedData = compressedDataStr.getBytes();
            String lat = AprsCoordinateFormat.latitude(CompressedDataFormatUtils.getLatitudeShort(compressedData));
            String lon = AprsCoordinateFormat.longitude(CompressedDataFormatUtils.getLongitudeShort(compressedData));

            String weatherData = content.substring(14);
            ret = parseWeatherData(packet.getCallsign(), weatherData);
            if (ret != null) {
                ret.setLatitude(lat);
                ret.setLongitude(lon);
            }
        } catch (Exception e) {
            objectLog.error(
                    String.format(
                            "Exception caught parsing weather packet - %s : %s",
                            packet.getCallsign(), packet.getCommand()),
                    e);
        }

        return ret;
    }

    private static boolean isCompressedPosition(String content, int positionStart) {
        char first = content.charAt(positionStart);
        return first < '0' || first > '9';
    }

    private static boolean isCompressedWeatherPosition(String content, int positionStart) {
        // Check the compressed position's weather symbol, not the packet length.
        if (content.length() < positionStart + 13 || content.charAt(positionStart + 9) != '_') return false;
        for (int index = positionStart + 1; index < positionStart + 9; index++) {
            char value = content.charAt(index);
            if (value < '!' || value > '{') return false;
        }
        return true;
    }

    private WelcomeCenterWeatherReport parseCompleteWithoutTimestamp(StationPacket packet) {
        WelcomeCenterWeatherReport ret = null;

        String content = packet.getCommand();
        if ((content == null) || (content.isBlank()) || (content.isEmpty())) {
            return ret;
        }

        int messageIndex = 0;

        try {
            if (content.length() <= 1) return null;
            if (isCompressedPosition(content, 1)) {
                return isCompressedWeatherPosition(content, 1)
                        ? parseCompleteWithoutTimestampCompressedLocation(packet)
                        : null;
            }
            if (content.length() < 19) return null;

            String lat = content.substring(1, 9);
            String lon = content.substring(10, 19);

            if ((!lon.endsWith("E")) && (!lon.endsWith("W"))) {
                lon = "0" + content.substring(10, 18);
                // bad lon - short one character
                messageIndex = 18;
            } else {
                messageIndex = 19;
            }

            if (content.length() < messageIndex + 8 || content.charAt(messageIndex) != '_') {
                // this must be an underscore
                return null;
            }
            messageIndex++;
            messageIndex += 7; // skip wind speed and direction

            String weatherData = content.substring(messageIndex);
            ret = parseWeatherData(packet.getCallsign(), weatherData);
            if (ret != null) {
                ret.setLatitude(lat);
                ret.setLongitude(lon);
            }
        } catch (Exception e) {
            objectLog.error(
                    String.format(
                            "Error parsing weather packet without timestamp - %s : %s", packet.getCallsign(), content),
                    e);
        }

        return ret;
    }

    private WelcomeCenterWeatherReport parsePositionless(StationPacket packet) {
        WelcomeCenterWeatherReport ret = null;
        try {
            //            String reportTime = packet.getCommand().substring(1, 9);
            String weatherData = packet.getCommand().substring(9);
            ret = parseWeatherData(packet.getCallsign(), weatherData);
        } catch (Exception e) {
            objectLog.error(
                    String.format(
                            "Exception caught parsing positionless weather packet - %s : %s",
                            packet.getCallsign(), packet.getCommand()),
                    e);
        }

        return ret;
    }

    private WelcomeCenterWeatherReport parseWeatherData(String callsign, String weatherData) {
        if (weatherData.startsWith(WEATHERREPORT_ULTW)) return null;
        WelcomeCenterWeatherReport report = new WelcomeCenterWeatherReport(
                UUID.randomUUID(), callsign, null, null, null, null, null, null, LocalDateTime.now());
        Set<Character> seen = new HashSet<>();
        int index = 0;
        while (index < weatherData.length()) {
            char field = weatherData.charAt(index++);
            // A software suffix or other comment ends the measurements. Do not scan
            // arbitrary trailing text for letters that resemble weather fields.
            if ("csgtrpPhbLl#".indexOf(field) < 0 || !seen.add(field)) break;
            int start = index;
            while (index < weatherData.length()) {
                char value = weatherData.charAt(index);
                if ((value >= '0' && value <= '9')
                        || value == '.'
                        || value == '-'
                        || (weatherData.charAt(start) == ' ' && value == ' ')) {
                    index++;
                } else {
                    break;
                }
            }
            String raw = weatherData.substring(start, index);
            int width = field == 'b' ? 5 : field == 'h' ? 2 : 3;
            if (raw.length() == width
                    && (raw.chars().allMatch(c -> c == '.') || raw.chars().allMatch(c -> c == ' '))) continue;
            String pattern = field == 't' ? "(?:[0-9]{3}|-[0-9]{2})" : "[0-9]{" + width + "}";
            if (!raw.matches(pattern)) {
                objectLog.debug("Invalid weather field from {}: {}{}", callsign, field, raw);
                continue;
            }
            float value = Integer.parseInt(raw);
            switch (field) {
                case 't' -> report.setTemperature(value);
                case 'h' -> report.setHumidity(value == 0 ? 100 : value);
                case 'b' -> report.setBarometricPressure(value);
                case 'L' -> report.setLuminosity(value);
                case 'l' -> report.setLuminosity(value + 1000);
                default -> {
                    /* Wind and rain are not stored in Welcome Center weather reports. */
                }
            }
        }
        return report;
    }
}
