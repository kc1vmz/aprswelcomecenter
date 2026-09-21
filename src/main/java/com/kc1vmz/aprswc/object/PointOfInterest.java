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
import java.util.UUID;

@Entity
@Table(name = "points_of_interest")
public class PointOfInterest {
    @Id
    private UUID id;

    @Version
    private long version;

    private long beaconGeneration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "welcome_center_id", nullable = false)
    @JsonIgnore
    private WelcomeCenter welcomeCenter;

    @Column(nullable = false, length = 9, unique = true)
    private String name;

    @Column(length = 40)
    private String description;

    @Column(nullable = false, length = 8)
    private String latitude;

    @Column(nullable = false, length = 9)
    private String longitude;

    @Column(nullable = false, length = 1)
    private String symbolCode;

    @Column(nullable = false, length = 1)
    private String symbolTableId;

    @Column(nullable = false)
    private boolean temporarilyUnavailable;

    public UUID getId() {
        return id;
    }

    public void setId(UUID value) {
        id = value;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        version = value;
    }

    public long getBeaconGeneration() {
        return beaconGeneration;
    }

    public void setBeaconGeneration(long value) {
        beaconGeneration = value;
    }

    public WelcomeCenter getWelcomeCenter() {
        return welcomeCenter;
    }

    public void setWelcomeCenter(WelcomeCenter value) {
        welcomeCenter = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String value) {
        latitude = value;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String value) {
        longitude = value;
    }

    public String getSymbolCode() {
        return symbolCode;
    }

    public void setSymbolCode(String value) {
        symbolCode = value;
    }

    public String getSymbolTableId() {
        return symbolTableId;
    }

    public void setSymbolTableId(String value) {
        symbolTableId = value;
    }

    public boolean isTemporarilyUnavailable() {
        return temporarilyUnavailable;
    }

    public void setTemporarilyUnavailable(boolean value) {
        temporarilyUnavailable = value;
    }
}
