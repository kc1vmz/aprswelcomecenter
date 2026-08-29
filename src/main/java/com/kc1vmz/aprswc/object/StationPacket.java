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
package com.kc1vmz.aprswc.object;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "station_packets")
public class StationPacket {
    @Id
    private UUID id;

    @Column(name = "packet_processor_id")
    private String packetProcessorId;

    @Column(length = 10)
    private String callsign;

    @Transient
    @JsonIgnore
    private String callsignTo;

    @Transient
    @JsonIgnore
    private String welcomeCenterId;

    private LocalDateTime receivedTime = LocalDateTime.now();
    private String command;

    @Lob
    private String header;

    /** Required only by JPA. */
    protected StationPacket() {}

    public StationPacket(
            UUID id,
            String packetProcessorId,
            String callsign,
            LocalDateTime receivedTime,
            String command,
            String header) {
        this.id = id;
        this.packetProcessorId = packetProcessorId;
        this.callsign = callsign;
        this.receivedTime = receivedTime == null ? LocalDateTime.now() : receivedTime;
        this.command = command;
        this.header = header;
    }

    public StationPacket(StationPacket obj) {
        this.id = obj.id;
        this.packetProcessorId = obj.packetProcessorId;
        this.callsign = obj.callsign;
        this.callsignTo = obj.callsignTo;
        this.welcomeCenterId = obj.welcomeCenterId;
        this.receivedTime = obj.receivedTime;
        this.command = obj.command;
        this.header = obj.header;
    }

    @PrePersist
    void initialize() {
        if (id == null) id = UUID.randomUUID();
        if (receivedTime == null) receivedTime = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID v) {
        id = v;
    }

    public String getPacketProcessorId() {
        return packetProcessorId;
    }

    public void setPacketProcessorId(String v) {
        packetProcessorId = v;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String v) {
        callsign = v;
    }

    public String getCallsignTo() {
        return callsignTo;
    }

    public void setCallsignTo(String callsignTo) {
        this.callsignTo = callsignTo;
    }

    public String getWelcomeCenterId() {
        return welcomeCenterId;
    }

    public void setWelcomeCenterId(String welcomeCenterId) {
        this.welcomeCenterId = welcomeCenterId;
    }

    public LocalDateTime getReceivedTime() {
        return receivedTime;
    }

    public void setReceivedTime(LocalDateTime v) {
        receivedTime = v == null ? LocalDateTime.now() : v;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String v) {
        command = v;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public byte[] convertHeaderToBytes() {
        if (header == null || header.isEmpty()) {
            return new byte[0];
        }
        if (header.length() % 2 != 0) {
            throw new IllegalArgumentException("Header must contain an even number of hexadecimal characters");
        }

        byte[] bytes = new byte[header.length() / 2];
        for (int index = 0; index < header.length(); index += 2) {
            try {
                bytes[index / 2] = (byte) Integer.parseInt(header.substring(index, index + 2), 16);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Header contains a non-hexadecimal character", exception);
            }
        }
        return bytes;
    }
}
