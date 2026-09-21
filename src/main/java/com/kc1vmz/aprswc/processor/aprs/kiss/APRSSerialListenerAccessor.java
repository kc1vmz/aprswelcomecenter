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

import com.fazecast.jSerialComm.SerialPort;
import com.kc1vmz.aprswc.communication.CommunicationConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class APRSSerialListenerAccessor extends APRSKISSListenerAccessor {
    private SerialPort serial;
    private boolean closed;

    public APRSSerialListenerAccessor(CommunicationConfig config) {
        super(config);
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
}
