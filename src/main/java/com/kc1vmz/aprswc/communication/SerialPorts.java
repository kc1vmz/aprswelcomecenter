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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Discovery only: never opens ports to test their availability. */
public final class SerialPorts {
    private SerialPorts() {}

    public record Assignment(String device, String name, boolean active) {}

    public record Device(String device, String description) {}

    public record Port(String device, String description, boolean detected, List<Assignment> assignments) {}

    public record Result(List<Port> ports, String error) {}

    public static Result discover(List<Assignment> assignments) {
        return discover(assignments, () -> {
            List<Device> devices = new ArrayList<>();
            boolean windows =
                    System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
            for (SerialPort port : SerialPort.getCommPorts())
                devices.add(new Device(
                        windows ? port.getSystemPortName() : port.getSystemPortPath(), port.getDescriptivePortName()));
            return devices;
        });
    }

    static Result discover(List<Assignment> assignments, Supplier<List<Device>> enumeration) {
        var detected = new LinkedHashMap<String, Device>();
        String error = null;
        try {
            for (Device device : enumeration.get()) detected.putIfAbsent(key(device.device()), device);
        } catch (RuntimeException | LinkageError failure) {
            error = "Port discovery is unavailable. You can still enter a serial device manually.";
        }
        var choices = new LinkedHashMap<String, Device>();
        detected.values().forEach(device -> choices.put(device.device(), device));
        for (Assignment assignment : assignments)
            choices.putIfAbsent(assignment.device(), new Device(assignment.device(), "Configured port"));
        List<Port> ports = choices.values().stream()
                .map(device -> {
                    String identity = key(device.device());
                    return new Port(
                            device.device(),
                            device.description(),
                            detected.containsKey(identity),
                            assignments.stream()
                                    .filter(a -> key(a.device()).equals(identity))
                                    .toList());
                })
                .sorted(Comparator.comparing(port -> sortKey(port.device())))
                .toList();
        return new Result(ports, error);
    }

    private static String key(String device) {
        return SerialDeviceIdentity.identity(device);
    }

    private static String sortKey(String device) {
        String value = device.toUpperCase(Locale.ROOT);
        if (value.matches("COM[0-9]{1,9}"))
            return "COM" + String.format(Locale.ROOT, "%09d", Long.parseLong(value.substring(3)));
        return value;
    }
}
