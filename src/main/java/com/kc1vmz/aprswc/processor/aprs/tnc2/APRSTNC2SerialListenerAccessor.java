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
package com.kc1vmz.aprswc.processor.aprs.tnc2;

import com.fazecast.jSerialComm.SerialPort;
import com.kc1vmz.aprswc.communication.*;
import com.kc1vmz.aprswc.constants.ApplicationToCallConstant;
import com.kc1vmz.aprswc.object.*;
import com.kc1vmz.aprswc.utils.APRSTime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

/** One accessor owns one connection. Reads never acquire the writer's lock. */
public class APRSTNC2SerialListenerAccessor implements CommunicationTransport {
    private SerialPort serial;
    private boolean closed;
    private final CommunicationConfig config;
    private InputStream input;
    private OutputStream output;

    protected APRSTNC2SerialListenerAccessor(CommunicationConfig config) {
        this.config = config;
    }

    public void connect() throws IOException, InterruptedException {
        synchronized (this) {
            if (closed) throw new IOException("Stopped");
            serial = SerialPort.getCommPort(config.serialDevice());
            serial.setComPortParameters(config.baudRate(), 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            serial.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
            serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 5000);
            if (!serial.openPort()) throw new IOException("Cannot open serial port");
            input = serial.getInputStream();
            output = serial.getOutputStream();
        }
        for (String command : new String[] {config.initCommand1(), config.initCommand2()}) {
            if (command != null && !command.isBlank()) {
                output.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
                output.flush();
                // Allow the TNC to apply each initialization command; stopping interrupts this delay.
                Thread.sleep(2000);
            }
        }
    }

    public synchronized void close() {
        closed = true;
        if (serial != null) serial.closePort();
    }

    public StationPacket read() throws IOException {
        byte[] packet = new byte[2048];
        int index = 0;
        boolean active = false;

        byte[] readBuffer = new byte[1];
        while (index < packet.length) {
            int numBytes = input.read(readBuffer);
            if ((numBytes == 0) && (!active)) {
                return null;
            }
            active = true;
            if (readBuffer[0] == 10) {
                break;
            } else {
                packet[index] = readBuffer[0];
                index++;
            }
        }

        return new StationPacket(
                UUID.randomUUID(), config.id().toString(), null, LocalDateTime.now(), new String(packet), null);
    }

    private synchronized void write(byte[] bytes) throws IOException {
        output.write(bytes);
        output.flush();
    }

    public void sendMessage(String from, String to, String content) throws IOException {
        write(String.format(
                        "%s>%s,%s::%-9s:%s\r\n",
                        from, ApplicationToCallConstant.TOCALL_NC2, config.digiPath(), to, content)
                .getBytes());
    }

    public void sendObject(ObjectBeacon b) throws IOException {
        write(String.format(
                        "%s>%s,%s:;%s\r\n",
                        b.getCallsignFrom(), ApplicationToCallConstant.TOCALL_NC2, config.digiPath(), objectData(b))
                .getBytes());
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
}
