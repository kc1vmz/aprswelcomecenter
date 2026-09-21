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

import com.kc1vmz.aprswc.object.ObjectBeacon;
import com.kc1vmz.aprswc.object.StationPacket;
import java.io.IOException;

public interface CommunicationTransport extends AutoCloseable {
    void connect() throws Exception;

    StationPacket read() throws IOException;

    void sendMessage(String from, String to, String content) throws IOException;

    void sendObject(ObjectBeacon beacon) throws IOException;

    void close();
}
