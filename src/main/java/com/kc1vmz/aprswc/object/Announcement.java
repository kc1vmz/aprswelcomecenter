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

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "announcements")
public class Announcement {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private WelcomeCenter welcomeCenter;

    @ManyToOne(optional = false)
    private CommunicationPolicy communicationPolicy;

    private LocalDateTime sentTime;
    /** Required only by JPA. */
    protected Announcement() {}

    public Announcement(
            UUID id, WelcomeCenter welcomeCenter, CommunicationPolicy communicationPolicy, LocalDateTime sentTime) {
        this.id = id;
        this.welcomeCenter = welcomeCenter;
        this.communicationPolicy = communicationPolicy;
        this.sentTime = sentTime;
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

    public CommunicationPolicy getCommunicationPolicy() {
        return communicationPolicy;
    }

    public void setCommunicationPolicy(CommunicationPolicy v) {
        communicationPolicy = v;
    }

    public LocalDateTime getSentTime() {
        return sentTime;
    }

    public void setSentTime(LocalDateTime v) {
        sentTime = v;
    }
}
