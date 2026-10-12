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

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;

/** Records only completed APRS writes, never login or device commands. */
public abstract class PacketLoggingTransport implements CommunicationTransport {
    private Consumer<String> packetLogger;

    protected PacketLoggingTransport() {
        this(packet -> {});
    }

    protected PacketLoggingTransport(Consumer<String> packetLogger) {
        this.packetLogger = packetLogger;
    }

    public void setPacketLogger(Consumer<String> packetLogger) {
        this.packetLogger = packetLogger;
    }

    protected void recordTransmitted(String packet) {
        try {
            packetLogger.accept(packet.replaceFirst("[\\r\\n]+$", ""));
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger(getClass()).error("Failed to record transmitted packet", failure);
        }
    }

    protected String ascii(String value) {
        return new String(value.getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII);
    }
}
