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

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "station_positions",
        indexes = @Index(name = "idx_station_position_station_created", columnList = "station_id, created_time"))
public class StationPosition {
    @Id
    private UUID id;

    private String longitude, latitude;

    @Column(name = "created_time")
    private LocalDateTime createdTime;

    private String callsign;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;
    /** Required only by JPA. */
    protected StationPosition() {}

    public StationPosition(UUID id, String callsign, String longitude, String latitude, LocalDateTime createdTime) {
        this.id = id;
        this.callsign = callsign;
        this.longitude = longitude;
        this.latitude = latitude;
        this.createdTime = createdTime;
    }

    @PrePersist
    void initialize() {
        if (id == null) id = UUID.randomUUID();
        if (createdTime == null) createdTime = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID v) {
        id = v;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String v) {
        longitude = v;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String v) {
        latitude = v;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime v) {
        createdTime = v;
    }

    public UUID getStationId() {
        return station == null ? null : station.getId();
    }

    @JsonProperty
    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station v) {
        station = v;
    }
}
