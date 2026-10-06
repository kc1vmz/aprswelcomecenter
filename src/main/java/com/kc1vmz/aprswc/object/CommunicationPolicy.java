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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.MessageType;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "communication_policies")
public class CommunicationPolicy {
    @Version
    private long version;

    @Embedded
    private PolicyAutomation automation;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(insertable = false, updatable = false)
    private Instant nextRunAt;

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        version = value;
    }

    public PolicyAutomation getAutomation() {
        return automation;
    }

    public void setAutomation(PolicyAutomation value) {
        automation = value;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private CommunicationCategory category;

    @ManyToOne(optional = false)
    private WelcomeCenter welcomeCenter;

    @Column(length = 4000)
    private String messageText;

    private LocalTime startTime, endTime;

    @Enumerated(EnumType.STRING)
    private MessageType messageType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "communication_event_type")
    private CommunicationEventType communicationEventType;
    /** Required only by JPA. */
    protected CommunicationPolicy() {}

    public CommunicationPolicy(
            UUID id,
            CommunicationCategory category,
            WelcomeCenter welcomeCenter,
            String messageText,
            LocalTime startTime,
            LocalTime endTime,
            MessageType messageType,
            CommunicationEventType communicationEventType) {
        this(
                id,
                category,
                welcomeCenter,
                messageText,
                startTime,
                endTime,
                messageType,
                communicationEventType,
                0,
                null,
                null);
    }

    public CommunicationPolicy(
            UUID id,
            CommunicationCategory category,
            WelcomeCenter welcomeCenter,
            String messageText,
            LocalTime startTime,
            LocalTime endTime,
            MessageType messageType,
            CommunicationEventType communicationEventType,
            long version,
            PolicyAutomation automation,
            Instant nextRunAt) {
        this.version = version;
        this.automation = automation;
        this.nextRunAt = nextRunAt;
        this.id = id;
        this.category = category;
        this.welcomeCenter = welcomeCenter;
        this.messageText = messageText;
        this.startTime = startTime;
        this.endTime = endTime;
        this.messageType = messageType;
        this.communicationEventType = communicationEventType;
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

    public CommunicationCategory getCategory() {
        return category;
    }

    public void setCategory(CommunicationCategory v) {
        category = v;
    }

    public WelcomeCenter getWelcomeCenter() {
        return welcomeCenter;
    }

    public void setWelcomeCenter(WelcomeCenter v) {
        welcomeCenter = v;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String v) {
        messageText = v;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime v) {
        startTime = v;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime v) {
        endTime = v;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType v) {
        messageType = v;
    }

    public CommunicationEventType getCommunicationEventType() {
        return communicationEventType;
    }

    public void setCommunicationEventType(CommunicationEventType communicationEventType) {
        this.communicationEventType = communicationEventType;
    }
}
