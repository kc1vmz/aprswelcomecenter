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
package com.kc1vmz.aprswc.processor.aprs.is;

import com.kc1vmz.aprswc.communication.*;
import com.kc1vmz.aprswc.constants.ApplicationToCallConstant;
import com.kc1vmz.aprswc.object.*;
import com.kc1vmz.aprswc.utils.APRSTime;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

/** One accessor owns one connection. Reads never acquire the writer's lock. */
public class APRSInternetServerListenerAccessor implements CommunicationTransport {
    private final CommunicationConfig config;
    private final APRSUtilityAccessor utility;
    private final Socket socket = new Socket();
    private BufferedReader reader;
    private Writer writer;

    public APRSInternetServerListenerAccessor(CommunicationConfig config, APRSUtilityAccessor utility) {
        this.config = config;
        this.utility = utility;
    }

    public void connect() throws IOException {
        socket.connect(new InetSocketAddress(config.host(), config.port()), 5000);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
        write(utility.generateAuthStr(new APRSAuthenticationInfo(config.username(), config.passcode()), config.filter())
                + "\r\n");
    }

    public StationPacket read() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new EOFException("Connection closed");
        if (line.startsWith("#")) return null;
        return new StationPacket(UUID.randomUUID(), config.id().toString(), null, LocalDateTime.now(), line, null);
    }

    private synchronized void write(String data) throws IOException {
        writer.write(data);
        writer.flush();
    }

    public void sendMessage(String from, String to, String content) throws IOException {
        write(String.format("%s>%s,TCPIP*::%-9s:%s\r\n", from, ApplicationToCallConstant.TOCALL_NC2, to, content));
    }

    public void sendObject(ObjectBeacon b) throws IOException {
        write(String.format(
                "%s>%s,TCPIP*:;%s\r\n", b.getCallsignFrom(), ApplicationToCallConstant.TOCALL_NC2, objectData(b)));
    }

    public static String objectData(ObjectBeacon b) {
        return String.format(
                "%-9s%s%s%s%s%s%s%s",
                b.getObjectName(),
                b.isActive() ? "*" : "_",
                APRSTime.convertZonedDateTimeToDDHHMM(ZonedDateTime.now()),
                b.getLatitude(),
                b.getSymbolId(),
                b.getLongitude(),
                b.getSymbolCode(),
                b.getStatusMessage());
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
