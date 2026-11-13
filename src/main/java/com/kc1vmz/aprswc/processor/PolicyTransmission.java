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

import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PolicyTransmission {
    private PolicyTransmission() {}

    public static boolean scheduled(CommunicationPolicy p) {
        return p.getCommunicationEventType() == CommunicationEventType.SCHEDULED_ONCE
                || p.getCommunicationEventType() == CommunicationEventType.SCHEDULED_RECURRING;
    }

    public static int limit(CommunicationPolicy p) {
        return scheduled(p) ? p.getMessageType() == MessageType.STATUS_REPORT ? 62 : 67 : 64;
    }

    public static void configure(CommunicationPolicy p) {
        if (!scheduled(p)) {
            p.setMessageType(MessageType.MESSAGE);
            p.setBulletinAddress(null);
            return;
        }
        if (p.getMessageType() == null || p.getMessageType() == MessageType.MESSAGE)
            p.setMessageType(MessageType.BULLETIN);
        if (p.getMessageType() == MessageType.STATUS_REPORT) {
            p.setBulletinAddress(null);
            return;
        }
        String address = p.getBulletinAddress();
        if (address == null) address = "BLN1";
        address = address.trim().toUpperCase(Locale.ROOT);
        if (!address.matches("BLN(?:[0-9A-Z][A-Z0-9-]{0,5})?"))
            throw invalid("Use BLN, or BLN plus a 0-9/A-Z ID and up to five letters, digits or hyphens");
        p.setBulletinAddress(address);
    }

    public static void validateText(CommunicationPolicy p, String text) {
        int max = limit(p);
        if (text == null
                || text.isBlank()
                || text.length() > max
                || text.chars()
                        .anyMatch(c -> c < 32
                                || c > 126
                                || c == '|'
                                || c == '~'
                                || (p.getMessageType() != MessageType.STATUS_REPORT && c == '{')))
            throw invalid(
                    "Message must contain 1-" + max + " printable ASCII characters without reserved APRS characters");
    }

    private static ResponseStatusException invalid(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
