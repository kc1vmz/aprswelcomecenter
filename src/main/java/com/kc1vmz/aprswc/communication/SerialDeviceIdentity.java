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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class SerialDeviceIdentity {
    private SerialDeviceIdentity() {}

    public static String identity(String device) {
        return identity(
                device, System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows"));
    }

    public static String identity(String device, boolean windows) {
        if (device == null || device.isBlank()) throw new IllegalArgumentException("Serial device is required");
        if (windows) {
            String normalized = device.strip().toUpperCase(Locale.ROOT);
            if (normalized.startsWith("\\\\.\\")) normalized = normalized.substring(4);
            if (!normalized.matches("COM[0-9]+")) return normalized;
            int number = Integer.parseInt(normalized.substring(3));
            if (number < 1) throw new IllegalArgumentException("Invalid COM port");
            return "COM" + number;
        }
        Path path = Path.of(device).toAbsolutePath().normalize();
        try {
            return path.toRealPath().toString();
        } catch (IOException missing) {
            return path.toString();
        }
    }
}
