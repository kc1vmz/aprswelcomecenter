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
import com.kc1vmz.aprswc.utils.CompressedDataFormatUtils;
import java.time.LocalDateTime;
import java.util.Arrays;
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

        if ((packet == null) || (packet.getCommand() == null)) {
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

        if (packet.getCommand().charAt(1) == '_') {
            return parseCompleteWithTimestampCompressedLocation(packet);
        }

        int messageIndex = 0;

        try {
            String content = packet.getCommand();
            String lat = content.substring(8, 16);
            String lon = content.substring(17, 26);

            if ((!lon.endsWith("E")) && (!lon.endsWith("W"))) {
                lon = "0" + content.substring(17, 25);
                // bad lon - short one character
                messageIndex = 25;
            } else {
                messageIndex = 26;
            }

            if (content.charAt(messageIndex) != '_') {
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
            String lat = CompressedDataFormatUtils.convertDecimalToDDMMSSx(
                    CompressedDataFormatUtils.getLatitudeShort(compressedData), "NS");
            String lon = CompressedDataFormatUtils.convertDecimalToDDDMMSSx(
                    CompressedDataFormatUtils.getLongitudeShort(compressedData), "EW");

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
            String lat = CompressedDataFormatUtils.convertDecimalToDDMMSSx(
                    CompressedDataFormatUtils.getLatitudeShort(compressedData), "NS");
            String lon = CompressedDataFormatUtils.convertDecimalToDDDMMSSx(
                    CompressedDataFormatUtils.getLongitudeShort(compressedData), "EW");

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

    private WelcomeCenterWeatherReport parseCompleteWithoutTimestamp(StationPacket packet) {
        WelcomeCenterWeatherReport ret = null;

        String content = packet.getCommand();
        if ((content == null) || (content.isBlank()) || (content.isEmpty())) {
            return ret;
        }

        int messageIndex = 0;

        try {
            if (content.length() <= 27) {
                return parseCompleteWithoutTimestampCompressedLocation(packet);
            }

            String lat = content.substring(1, 9);
            String lon = content.substring(10, 19);

            if ((!lon.endsWith("E")) && (!lon.endsWith("W"))) {
                lon = "0" + content.substring(10, 18);
                // bad lon - short one character
                messageIndex = 18;
            } else {
                messageIndex = 19;
            }

            if (content.charAt(messageIndex) != '_') {
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
        WelcomeCenterWeatherReport ret = new WelcomeCenterWeatherReport(
                UUID.randomUUID(), callsign, null, null, null, null, null, null, LocalDateTime.now());

        byte[] weatherData_bytes = weatherData.getBytes();
        if (ultPacket(weatherData_bytes)) {
            return null;
        }
        int index = 0;
        boolean seenS = false;
        boolean stop = false;
        boolean[] lettersProcessed = createBitmask();

        while ((index < weatherData_bytes.length) && (!stop)) {
            char type = (char) weatherData_bytes[index];
            byte[] value_bytes;

            try {
                switch (type) {
                    case WEATHERREPORT_FIELD_WIND_DIRECTION: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_WIND_DIRECTION]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                        index += 4;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            /// ignore String value = new String(value_bytes);
                            /// ignore ret.setWindDirection(Integer.parseInt(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_WIND_DIRECTION] = true;
                        break;
                    case WEATHERREPORT_FIELD_WIND_SPEED: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_WIND_SPEED]) {
                            stop = true;
                            break;
                        }
                        if (!seenS) {
                            // first time through - wind speed
                            value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                            index += 4;
                            if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                                // get the value
                                /// ignore String value = new String(value_bytes);
                                /// ignore ret.setWindSpeed(Integer.parseInt(value));
                            }
                        } else {
                            // if seen again, maybe it is snow. APRS spec shows used for both
                            value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                            index += 4;
                            if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                                // get the value
                                String value = new String(value_bytes);
                                if (value.contains(".")) {
                                    String[] snow_parts = value.split(".");
                                    if ((snow_parts != null) && (snow_parts.length == 2)) {
                                        /// ignore ret.setSnowfallLast24Hr(Integer.parseInt(snow_parts[0]));
                                    }
                                } else {
                                    /// ignore ret.setSnowfallLast24Hr(Integer.parseInt(value));
                                }
                            }
                        }
                        seenS = true;
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_WIND_SPEED] = true;
                        break;
                    case WEATHERREPORT_FIELD_WIND_GUST: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_WIND_GUST]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                        index += 4;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            /// ignore String value = new String(value_bytes);
                            /// ignore ret.setGust(Integer.parseInt(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_WIND_GUST] = true;
                        break;
                    case WEATHERREPORT_FIELD_TEMPERATURE: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_TEMPERATURE]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                        index += 4;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            String value = new String(value_bytes);
                            ret.setTemperature(Float.parseFloat(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_TEMPERATURE] = true;
                        break;
                    case WEATHERREPORT_FIELD_RAIN_LAST_HOUR: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_RAIN_LAST_HOUR]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                        index += 4;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            /// ignore String value = new String(value_bytes);
                            /// ignore ret.setRainfallLast1Hr(Integer.parseInt(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_RAIN_LAST_HOUR] = true;
                        break;
                    case WEATHERREPORT_FIELD_RAIN_LAST_24HOUR: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_RAIN_LAST_24HOUR]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                        index += 4;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            /// ignore String value = new String(value_bytes);
                            /// ignore  ret.setRainfallLast24Hr(Integer.parseInt(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_RAIN_LAST_24HOUR] = true;
                        break;
                    case WEATHERREPORT_FIELD_RAIN_SINCE_MIDNIGHT: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_RAIN_SINCE_MIDNIGHT]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                        index += 4;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            /// ignore String value = new String(value_bytes);
                            /// ignore ret.setRainfallSinceMidnight(Integer.parseInt(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_RAIN_SINCE_MIDNIGHT] = true;
                        break;
                    case WEATHERREPORT_FIELD_HUMIDITY: // value in next *2* bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_HUMIDITY]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 3);
                        index += 3;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            String value = new String(value_bytes);
                            ret.setHumidity(Float.parseFloat(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_HUMIDITY] = true;
                        break;
                    case WEATHERREPORT_FIELD_BAROMETRIC_PRESSURE: // value in next *5* bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_BAROMETRIC_PRESSURE]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 6);
                        index += 6;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            String value = new String(value_bytes);
                            ret.setBarometricPressure(Float.parseFloat(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_BAROMETRIC_PRESSURE] = true;
                        break;
                    case WEATHERREPORT_FIELD_LUMOSITY: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_LUMOSITY]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                        index += 4;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            String value = new String(value_bytes);
                            ret.setLuminosity(Float.parseFloat(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_LUMOSITY] = true;
                        break;
                    case WEATHERREPORT_FIELD_LUMOSITY_LARGE: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_LUMOSITY_LARGE]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                        index += 4;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            String value = new String(value_bytes);
                            ret.setLuminosity(Float.parseFloat(value) + 1000);
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_LUMOSITY_LARGE] = true;
                        break;
                    case WEATHERREPORT_FIELD_RAIN_RAW: // value in next 3 bytes
                        if (lettersProcessed[INDEX_WEATHERREPORT_FIELD_RAIN_RAW]) {
                            stop = true;
                            break;
                        }
                        value_bytes = Arrays.copyOfRange(weatherData_bytes, index + 1, index + 4);
                        index += 3;
                        if ((value_bytes[0] != ' ') && (value_bytes[0] != '.')) {
                            // get the value
                            /// ignore String value = new String(value_bytes);
                            /// ignore ret.setRawRainCounter(Integer.parseInt(value));
                        }
                        lettersProcessed[INDEX_WEATHERREPORT_FIELD_RAIN_RAW] = true;
                        break;
                    default:
                        index++;
                        stop = true;
                        // found something we should not have - get out
                        break;
                }
            } catch (Exception e) {
                objectLog.warn(String.format("Error parsing weather data - %s", weatherData_bytes), e);
                break;
            }
        }

        return ret;
    }

    private static boolean ultPacket(byte[] weatherData_bytes) {
        String weatherData = new String(weatherData_bytes);
        return weatherData.startsWith(WEATHERREPORT_ULTW);
    }
}
