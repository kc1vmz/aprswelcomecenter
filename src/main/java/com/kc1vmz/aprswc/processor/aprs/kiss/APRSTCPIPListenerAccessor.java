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

import com.kc1vmz.aprswc.communication.CommunicationConfig;
import java.io.IOException;
import java.net.*;

public class APRSTCPIPListenerAccessor extends APRSKISSListenerAccessor {
    private final Socket socket = new Socket();

    public APRSTCPIPListenerAccessor(CommunicationConfig config) {
        super(config);
    }

    public void connect() throws IOException {
        socket.connect(new InetSocketAddress(config.host(), config.port()), 5000);
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
