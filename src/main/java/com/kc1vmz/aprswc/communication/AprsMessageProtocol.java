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
package com.kc1vmz.aprswc.communication;

import java.util.regex.Pattern;

/** Parsing for message payloads only: binary position data must never be trimmed. */
public final class AprsMessageProtocol {
    private static final Pattern CONTROL =
            Pattern.compile("^(ack|rej)([A-Za-z0-9]{1,5})(?:}[A-Za-z0-9]{0,5}|[\\r\\n]*\\{[A-Za-z0-9]{1,5})?$");
    private static final Pattern IDENTIFIER = Pattern.compile("\\{([A-Za-z0-9]{1,5})(?:}([A-Za-z0-9]{0,5}))?$");

    private AprsMessageProtocol() {}

    public record Control(String kind, String identifier) {
        public String wireText() {
            return kind + identifier;
        }
    }

    public static String normalize(String text) {
        if (text == null) return null;
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\r' || text.charAt(end - 1) == '\n')) end--;
        return text.substring(0, end);
    }

    public static Control control(String text) {
        if (text == null) return null;
        var matcher = CONTROL.matcher(normalize(text));
        return matcher.matches() ? new Control(matcher.group(1), matcher.group(2)) : null;
    }

    public static String identifier(String text) {
        if (text == null) return null;
        var matcher = IDENTIFIER.matcher(normalize(text));
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String replyAcknowledgement(String text) {
        if (text == null) return null;
        var matcher = IDENTIFIER.matcher(normalize(text));
        return matcher.find() && matcher.group(2) != null && !matcher.group(2).isEmpty() ? matcher.group(2) : null;
    }

    public static String body(String text) {
        String normalized = normalize(text);
        if (normalized == null) return null;
        var matcher = IDENTIFIER.matcher(normalized);
        return matcher.find() ? normalized.substring(0, matcher.start()) : normalized;
    }
}
