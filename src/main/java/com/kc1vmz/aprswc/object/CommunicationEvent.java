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

import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "communication_events")
public class CommunicationEvent {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private WelcomeCenter welcomeCenter;

    @Enumerated(EnumType.STRING)
    private CommunicationEventType type;

    private UUID context;

    private String callsign;

    private String packetProcessorId;

    /** Required only by JPA. */
    protected CommunicationEvent() {}

    public CommunicationEvent(
            UUID id,
            WelcomeCenter welcomeCenter,
            CommunicationEventType type,
            UUID context,
            String callsign,
            String packetProcessorId) {
        this.id = id;
        this.welcomeCenter = welcomeCenter;
        this.type = type;
        this.context = context;
        this.callsign = callsign;
        this.packetProcessorId = packetProcessorId;
    }

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID v) {
        id = v;
    }

    public WelcomeCenter getWelcomeCenter() {
        return welcomeCenter;
    }

    public void setWelcomeCenter(WelcomeCenter v) {
        welcomeCenter = v;
    }

    public CommunicationEventType getType() {
        return type;
    }

    public void setType(CommunicationEventType v) {
        type = v;
    }

    public UUID getContext() {
        return context;
    }

    public void setContext(UUID v) {
        context = v;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String v) {
        callsign = v;
    }

    public String getPacketProcessorId() {
        return packetProcessorId;
    }

    public void setPacketProcessorId(String v) {
        packetProcessorId = v;
    }
}
