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
import com.kc1vmz.aprswc.enumeration.*;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "welcome_regions")
public class WelcomeRegion {
    @Id
    private UUID id;

    private String name;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    private RegionType type;

    private String topLeftLongitude,
            topLeftLatitude,
            bottomRightLongitude,
            bottomRightLatitude,
            centerLongitude,
            centerLatitude;
    private Integer diameter;

    @Enumerated(EnumType.STRING)
    private DistanceUnit unit;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "welcome_center_id")
    private WelcomeCenter welcomeCenter;
    /** Required only by JPA. */
    protected WelcomeRegion() {}

    public WelcomeRegion(
            UUID id,
            String name,
            String description,
            RegionType type,
            String topLeftLongitude,
            String topLeftLatitude,
            String bottomRightLongitude,
            String bottomRightLatitude,
            String centerLongitude,
            String centerLatitude,
            Integer diameter,
            DistanceUnit unit) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.topLeftLongitude = topLeftLongitude;
        this.topLeftLatitude = topLeftLatitude;
        this.bottomRightLongitude = bottomRightLongitude;
        this.bottomRightLatitude = bottomRightLatitude;
        this.centerLongitude = centerLongitude;
        this.centerLatitude = centerLatitude;
        this.diameter = diameter;
        this.unit = unit;
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

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    public RegionType getType() {
        return type;
    }

    public void setType(RegionType v) {
        type = v;
    }

    public String getTopLeftLongitude() {
        return topLeftLongitude;
    }

    public void setTopLeftLongitude(String v) {
        topLeftLongitude = v;
    }

    public String getTopLeftLatitude() {
        return topLeftLatitude;
    }

    public void setTopLeftLatitude(String v) {
        topLeftLatitude = v;
    }

    public String getBottomRightLongitude() {
        return bottomRightLongitude;
    }

    public void setBottomRightLongitude(String v) {
        bottomRightLongitude = v;
    }

    public String getBottomRightLatitude() {
        return bottomRightLatitude;
    }

    public void setBottomRightLatitude(String v) {
        bottomRightLatitude = v;
    }

    public String getCenterLongitude() {
        return centerLongitude;
    }

    public void setCenterLongitude(String v) {
        centerLongitude = v;
    }

    public String getCenterLatitude() {
        return centerLatitude;
    }

    public void setCenterLatitude(String v) {
        centerLatitude = v;
    }

    public Integer getDiameter() {
        return diameter;
    }

    public void setDiameter(Integer v) {
        diameter = v;
    }

    public DistanceUnit getUnit() {
        return unit;
    }

    public void setUnit(DistanceUnit v) {
        unit = v;
    }

    public WelcomeCenter getWelcomeCenter() {
        return welcomeCenter;
    }

    public void setWelcomeCenter(WelcomeCenter v) {
        welcomeCenter = v;
    }
}
