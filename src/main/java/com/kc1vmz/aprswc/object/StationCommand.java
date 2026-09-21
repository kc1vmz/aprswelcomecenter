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

import com.kc1vmz.aprswc.enumeration.StationCommandType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "station_commands")
public class StationCommand {
    @Id
    private UUID id;

    @Column(length = 10)
    private String callsign;

    @ManyToOne
    private WelcomeCenter welcomeCenter;

    private LocalDateTime receivedTime;
    private String command;

    @Transient
    private String packetProcessorId;

    @Enumerated(EnumType.STRING)
    private StationCommandType type;
    /** Required only by JPA. */
    protected StationCommand() {}

    public StationCommand(
            UUID id,
            String callsign,
            WelcomeCenter welcomeCenter,
            LocalDateTime receivedTime,
            String command,
            StationCommandType type) {
        this.id = id;
        this.callsign = callsign;
        this.welcomeCenter = welcomeCenter;
        this.receivedTime = receivedTime;
        this.command = command;
        this.type = type;
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

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String v) {
        callsign = v;
    }

    public WelcomeCenter getWelcomeCenter() {
        return welcomeCenter;
    }

    public void setWelcomeCenter(WelcomeCenter v) {
        welcomeCenter = v;
    }

    public LocalDateTime getReceivedTime() {
        return receivedTime;
    }

    public void setReceivedTime(LocalDateTime v) {
        receivedTime = v;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String v) {
        command = v;
    }

    public StationCommandType getType() {
        return type;
    }

    public void setType(StationCommandType v) {
        type = v;
    }

    public String getPacketProcessorId() {
        return packetProcessorId;
    }

    public void setPacketProcessorId(String value) {
        packetProcessorId = value;
    }
}
