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

import com.kc1vmz.aprswc.object.CommunicationInstance;
import java.util.UUID;

/** Immutable snapshot. Never log this record: it contains the APRS-IS passcode. */
public record CommunicationConfig(
        UUID id,
        long version,
        String type,
        String host,
        Integer port,
        String username,
        String passcode,
        String filter,
        String serialDevice,
        Integer baudRate,
        String initCommand1,
        String initCommand2,
        String digiPath) {
    public static CommunicationConfig from(CommunicationInstance c) {
        return new CommunicationConfig(
                c.getId(),
                c.getVersion(),
                c.getType(),
                c.getHost(),
                c.getPort(),
                c.getUsername(),
                c.getPasscode(),
                c.getFilter(),
                c.getSerialDevice(),
                c.getBaudRate(),
                c.getInitCommand1(),
                c.getInitCommand2(),
                c.getDigiPath());
    }

    @Override
    public String toString() {
        return "CommunicationConfig[" + id + ", " + type + "]";
    }
}
