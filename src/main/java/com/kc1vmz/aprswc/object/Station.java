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
import com.kc1vmz.aprswc.enumeration.StationState;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.*;

@Entity
@Table(name = "stations", uniqueConstraints = @UniqueConstraint(name = "uk_station_callsign", columnNames = "callsign"))
public class Station {
    @Id
    private UUID id;

    @NotBlank
    @Size(max = 10)
    @Column(nullable = false, length = 10)
    private String callsign;

    @Enumerated(EnumType.STRING)
    private StationState state;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<StationPosition> positions = new ArrayList<>();
    /** Required only by JPA. */
    protected Station() {}

    public Station(UUID id, String callsign, StationState state, List<StationPosition> positions) {
        this.id = id;
        this.callsign = callsign;
        this.state = state;
        if (positions != null) positions.forEach(this::addPosition);
    }

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }

    public void addPosition(StationPosition p) {
        p.setStation(this);
        positions.add(p);
    }

    public void removePosition(StationPosition p) {
        positions.remove(p);
        p.setStation(null);
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

    public StationState getState() {
        return state;
    }

    public void setState(StationState v) {
        state = v;
    }

    public List<StationPosition> getPositions() {
        return positions;
    }
}
