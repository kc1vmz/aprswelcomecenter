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

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SerialPortsTest {
    @Test
    void discoverySortsAndKeepsDisconnectedAssignments() {
        var assigned = List.of(
                new SerialPorts.Assignment("COM2", "Radio", true),
                new SerialPorts.Assignment("COM9", "Unplugged", false));
        var result = SerialPorts.discover(
                assigned, () -> List.of(new SerialPorts.Device("COM10", "USB"), new SerialPorts.Device("COM2", "TNC")));
        assertThat(result.error()).isNull();
        assertThat(result.ports()).extracting(SerialPorts.Port::device).containsExactly("COM2", "COM9", "COM10");
        assertThat(result.ports().get(0).assignments()).containsExactly(assigned.get(0));
        assertThat(result.ports().get(0).detected()).isTrue();
        assertThat(result.ports().get(1).detected()).isFalse();
    }

    @Test
    void unavailableLibraryPreservesManualConfiguration() {
        var result = SerialPorts.discover(List.of(new SerialPorts.Assignment("COM9", "Radio", false)), () -> {
            throw new UnsatisfiedLinkError("native library unavailable");
        });
        assertThat(result.error()).contains("manually");
        assertThat(result.ports()).hasSize(1);
        assertThat(result.ports().getFirst().detected()).isFalse();
        assertThat(SerialPorts.discover(List.of(), List::of).ports()).isEmpty();
    }

    @Test
    void serialIdentityNormalizesWindowsAliasesAndLinuxPaths() {
        assertThat(SerialDeviceIdentity.identity("\\\\.\\com02", true)).isEqualTo("COM2");
        assertThat(SerialDeviceIdentity.identity("/dev/serial/../ttyUSB0", false))
                .isEqualTo(SerialDeviceIdentity.identity("/dev/ttyUSB0", false));
    }
}
