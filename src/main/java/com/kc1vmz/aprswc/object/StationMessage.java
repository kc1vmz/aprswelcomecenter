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

import com.kc1vmz.aprswc.enumeration.MessageType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "station_messages")
public class StationMessage {
    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean requiresOpenCenter;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isRequiresOpenCenter() {
        return requiresOpenCenter;
    }

    public void setRequiresOpenCenter(boolean value) {
        requiresOpenCenter = value;
    }

    @Id
    private UUID id;

    @Column(length = 10)
    private String callsignTo;

    @Column(length = 10)
    private String callsignFrom;

    @ManyToOne
    private WelcomeCenter welcomeCenter;

    private LocalDateTime sentTime;

    @Column(nullable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonProperty(
            access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdTime;

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    @Column(length = 4000)
    private String content;

    private String packetProcessorId;

    @Enumerated(EnumType.STRING)
    private MessageType messageType = MessageType.MESSAGE;

    /** Required only by JPA. */
    protected StationMessage() {}

    public StationMessage(
            UUID id,
            String callsignTo,
            String callsignFrom,
            WelcomeCenter welcomeCenter,
            LocalDateTime sentTime,
            String content,
            String packetProcessorId,
            MessageType messageType) {
        this.id = id;
        this.callsignTo = callsignTo;
        this.callsignFrom = callsignFrom;
        this.welcomeCenter = welcomeCenter;
        this.sentTime = sentTime;
        this.content = content;
        this.packetProcessorId = packetProcessorId;
        this.messageType = messageType;
    }

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
        if (createdTime == null) createdTime = LocalDateTime.now();
        if (messageType == null) messageType = MessageType.MESSAGE;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID v) {
        id = v;
    }

    public String getCallsignTo() {
        return callsignTo;
    }

    public void setCallsignTo(String v) {
        callsignTo = v;
    }

    public String getCallsignFrom() {
        return callsignFrom;
    }

    public void setCallsignFrom(String v) {
        callsignFrom = v;
    }

    public WelcomeCenter getWelcomeCenter() {
        return welcomeCenter;
    }

    public void setWelcomeCenter(WelcomeCenter v) {
        welcomeCenter = v;
    }

    public LocalDateTime getSentTime() {
        return sentTime;
    }

    public void setSentTime(LocalDateTime v) {
        sentTime = v;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String v) {
        content = v;
    }

    public String getPacketProcessorId() {
        return packetProcessorId;
    }

    public void setPacketProcessorId(String v) {
        packetProcessorId = v;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType value) {
        messageType = value;
    }
}
