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

import com.fazecast.jSerialComm.SerialPort;
import com.kc1vmz.aprswc.constants.ApplicationToCallConstant;
import com.kc1vmz.aprswc.object.ObjectBeacon;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.processor.aprs.tnc2.APRSTNC2SerialListenerAccessor;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/** A single Kenwood command-mode serial connection, managed by the shared worker. */
public final class KenwoodSerialTransport implements CommunicationTransport {
    private final CommunicationConfig config;
    private SerialPort serial;
    private volatile KenwoodSession session;
    private boolean closed;

    public KenwoodSerialTransport(CommunicationConfig config) {
        this.config = config;
    }

    @Override
    public void connect() throws IOException {
        KenwoodSession current;
        synchronized (this) {
            if (closed) throw new IOException("Stopped");
            serial = SerialPort.getCommPort(config.serialDevice());
            serial.setComPortParameters(config.baudRate(), 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            serial.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
            serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, 1000, 5000);
            if (!serial.openPort()) throw new IOException("Cannot open serial port");
            current = new KenwoodSession(serial.getInputStream(), serial.getOutputStream(), config);
            session = current;
        }
        current.initialize();
    }

    @Override
    public StationPacket read() throws IOException {
        String line = session.read();
        return line == null
                ? null
                : new StationPacket(UUID.randomUUID(), config.id().toString(), null, LocalDateTime.now(), line, null);
    }

    @Override
    public void sendMessage(String from, String to, String content) throws IOException {
        if (to == null || !to.matches("[A-Za-z0-9-]{1,9}")) throw new IOException("Invalid message destination");
        session.send(ApplicationToCallConstant.TOCALL_NC2, String.format(":%-9s:%s", to, content));
    }

    @Override
    public void sendObject(ObjectBeacon beacon) throws IOException {
        session.send(ApplicationToCallConstant.TOCALL_NC2, ";" + APRSTNC2SerialListenerAccessor.objectData(beacon));
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (session != null) session.close();
        if (serial != null) serial.closePort();
    }
}
