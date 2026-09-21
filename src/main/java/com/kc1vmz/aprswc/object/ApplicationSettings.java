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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Entity
@Table(name = "application_settings")
public class ApplicationSettings {
    public static final int DEFAULT_PACKET_RETENTION_DAYS = 1;

    public static final int DEFAULT_STATION_RETENTION_DAYS = 10;
    public static final int DEFAULT_MESSAGE_RETENTION_DAYS = 10;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer stationRetentionDays = DEFAULT_STATION_RETENTION_DAYS;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer messageRetentionDays = DEFAULT_MESSAGE_RETENTION_DAYS;

    public Integer getStationRetentionDays() {
        return stationRetentionDays;
    }

    public void setStationRetentionDays(Integer value) {
        stationRetentionDays = value;
    }

    public Integer getMessageRetentionDays() {
        return messageRetentionDays;
    }

    public void setMessageRetentionDays(Integer value) {
        messageRetentionDays = value;
    }

    @Id
    private UUID id;

    private String mapTileUrl;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer packetRetentionDays = DEFAULT_PACKET_RETENTION_DAYS;

    public Integer getPacketRetentionDays() {
        return packetRetentionDays;
    }

    public void setPacketRetentionDays(Integer value) {
        packetRetentionDays = value;
    }

    protected ApplicationSettings() {}

    public ApplicationSettings(UUID id, String mapTileUrl) {
        this.id = id;
        this.mapTileUrl = mapTileUrl;
    }

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMapTileUrl() {
        return mapTileUrl;
    }

    public void setMapTileUrl(String value) {
        mapTileUrl = value;
    }
}
