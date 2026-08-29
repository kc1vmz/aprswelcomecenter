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

import com.kc1vmz.aprswc.enumeration.PacketType;
import com.kc1vmz.aprswc.object.StationPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PacketParser {
    private static final Logger logger = LoggerFactory.getLogger("PacketParser");

    public PacketType parse(StationPacket packet) {
        String content = packet.getCommand();
        PacketType ret = PacketType.UNKNOWN;

        if ((content == null) || (content.isBlank()) || (content.isEmpty())) {
            return ret;
        }
        try {
            int indexCallsignSep = content.indexOf(">");
            if (indexCallsignSep == -1) {
                logger.error("Exception finding callsign termination");
                return ret;
            }
            String callsign = content.substring(0, indexCallsignSep);
            packet.setCallsign(callsign);
            content = content.substring(indexCallsignSep + 1); // skip over callsign>
            // destination
            int indexDestSep = content.indexOf(",");
            if (indexDestSep == -1) {
                logger.error("Exception finding destination termination");
                return ret;
            }
            String callsignDest = content.substring(0, indexDestSep);
            packet.setCallsignTo(callsignDest);
        } catch (Exception e) {
            logger.error("Exception finding callsign termination", e);
            return ret;
        }

        // skip past digi/path
        try {
            int indexPathSep = content.indexOf(":");
            if (indexPathSep == -1) {
                logger.error("Exception finding path separator");
                return ret;
            }
            content = content.substring(indexPathSep + 1);
        } catch (Exception e) {
            logger.error("Exception finding path separator", e);
            return ret;
        }

        // look at first character
        switch (content.charAt(0)) {
            case '*':
            case '#':
            case '_':
                ret = PacketType.WEATHER;
                break;
            case '@':
            case '/':
            case '=':
            case '`':
            case '$':
            case '!':
                ret = PacketType.LOCATION;
                break;
            case 0x1c:
            case 0x1d:
            case '\'':
                ret = PacketType.MICE;
                break;
            case ':':
                ret = PacketType.MESSAGE;
                break;
            default:
                ret = PacketType.UNKNOWN;
                break;
        }

        packet.setCommand(content);

        return ret;
    }
}
