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
package com.kc1vmz.aprswc.processor.aprs.kiss;

import com.kc1vmz.aprswc.communication.*;
import com.kc1vmz.aprswc.constants.ApplicationToCallConstant;
import com.kc1vmz.aprswc.object.*;
import com.kc1vmz.aprswc.processor.aprs.is.APRSInternetServerListenerAccessor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** Per-instance KISS framing shared by TCP and serial accessors. */
public abstract class APRSKISSListenerAccessor implements CommunicationTransport {
    protected final CommunicationConfig config;
    protected InputStream input;
    protected OutputStream output;

    protected APRSKISSListenerAccessor(CommunicationConfig config) {
        this.config = config;
    }

    public StationPacket read() throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        boolean started = false, escaped = false;
        while (true) {
            int b = input.read();
            if (b < 0) throw new EOFException("Connection closed");
            if (b == 0xc0) {
                if (started && frame.size() > 0) break;
                started = true;
                continue;
            }
            if (!started) continue;
            if (b == 0xdb) {
                escaped = true;
                continue;
            }
            if (escaped) {
                b = b == 0xdc ? 0xc0 : b == 0xdd ? 0xdb : b;
                escaped = false;
            }
            frame.write(b);
            if (frame.size() > 4096) {
                frame.reset();
                started = false;
            }
        }
        byte[] bytes = frame.toByteArray();
        if (bytes.length < 17 || (bytes[0] & 15) != 0) return null;
        List<String> addresses = new ArrayList<>();
        int offset = 1;
        boolean last = false;
        while (!last) {
            if (offset + 7 > bytes.length || addresses.size() > 10) return null;
            StringBuilder call = new StringBuilder();
            for (int i = 0; i < 6; i++) call.append((char) ((bytes[offset + i] & 255) >> 1));
            int ssid = (bytes[offset + 6] >> 1) & 15;
            addresses.add(call.toString().trim() + (ssid == 0 ? "" : "-" + ssid));
            last = (bytes[offset + 6] & 1) != 0;
            offset += 7;
        }
        if (addresses.size() < 2
                || offset + 2 > bytes.length
                || bytes[offset] != 3
                || (bytes[offset + 1] & 255) != 0xf0) return null;
        String from = addresses.get(1);
        String path = addresses.get(0)
                + (addresses.size() > 2 ? "," + String.join(",", addresses.subList(2, addresses.size())) : "");
        String data = new String(bytes, offset + 2, bytes.length - offset - 2, StandardCharsets.US_ASCII);
        KISSPacket packet = new KISSPacket();
        packet.setCallsignFrom(from);
        packet.setCallsignTo(addresses.get(0));
        return new StationPacket(
                UUID.randomUUID(),
                config.id().toString(),
                from,
                LocalDateTime.now(),
                from + ">" + path + ":" + data,
                HexFormat.of().withUpperCase().formatHex(packet.getHeaderBytes()));
    }

    private KISSPacket packet(String from, String to, String data) {
        KISSPacket p = new KISSPacket();
        p.setCallsignFrom(from);
        p.setCallsignTo(to);
        p.setApplicationName(ApplicationToCallConstant.TOCALL_NC2);
        p.setData(data);
        p.setValid(true);
        p.setDigipeaters(Arrays.stream(Objects.toString(config.digiPath(), "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        return p;
    }

    private synchronized void write(byte[] bytes) throws IOException {
        output.write(KissPacketBuilder.build(bytes, (byte) 0));
        output.flush();
    }

    public void sendMessage(String from, String to, String content) throws IOException {
        write(AX25PacketBuilder.buildPacket(packet(from, to, content), ":"));
    }

    public void sendObject(ObjectBeacon b) throws IOException {
        write(AX25PacketBuilder.buildObjectPacket(
                packet(b.getObjectName(), null, ";" + APRSInternetServerListenerAccessor.objectData(b))));
    }
}
