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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.JdbcTypeCode;
import com.kc1vmz.aprswc.enumeration.WelcomeCenterStatus;

@Entity
@Table(
        name = "welcome_centers",
        uniqueConstraints = @UniqueConstraint(name = "uk_welcome_center_callsign", columnNames = "callsign"))
public class WelcomeCenter {
    @Id
    private UUID id;

    @Column(nullable = false, length = 8)
    private String communicationMode = "ALL";

    @Column(nullable = false)
    private long routingVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "welcome_center_communications", joinColumns = @JoinColumn(name = "welcome_center_id"))
    @Column(name = "communication_instance_id", nullable = false)
    private Set<UUID> communicationInstanceIds = new HashSet<>();

    public String getCommunicationMode() {
        return communicationMode;
    }

    public void setCommunicationMode(String value) {
        communicationMode = value;
    }

    public long getRoutingVersion() {
        return routingVersion;
    }

    public void setRoutingVersion(long value) {
        routingVersion = value;
    }

    public Set<UUID> getCommunicationInstanceIds() {
        return communicationInstanceIds;
    }

    public void setCommunicationInstanceIds(Set<UUID> value) {
        communicationInstanceIds = value;
    }

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 6)
    private WelcomeCenterStatus status;

    public WelcomeCenterStatus getStatus() {
        return status;
    }

    public void setStatus(WelcomeCenterStatus status) {
        this.status = status;
    }

    @JsonIgnore
    public boolean isOpen() {
        return status == WelcomeCenterStatus.OPEN;
    }

    private String name;

    @Column(length = 4000)
    private String description;

    @NotBlank
    @Size(max = 10)
    @Column(nullable = false, length = 10)
    private String callsign;

    private String ownerName;

    @Size(max = 10)
    private String ownerCallsign;

    private String organizationName;

    @Size(max = 10)
    private String organizationCallsign;

    private String contactName;

    @Size(max = 10)
    private String contactCallsign;

    private String longitude;

    private String latitude;

    private String symbolCode;

    private String symbolId;

    @OneToMany(mappedBy = "welcomeCenter", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<WelcomeRegion> regions = new ArrayList<>();

    /** Required only by JPA. */
    protected WelcomeCenter() {}

    public WelcomeCenter(
            UUID id,
            String name,
            String description,
            String callsign,
            String ownerName,
            String ownerCallsign,
            String organizationName,
            String organizationCallsign,
            String contactName,
            String contactCallsign,
            String longitude,
            String latitude,
            String symbolCode,
            String symbolId,
            List<WelcomeRegion> regions) {
        this.status = WelcomeCenterStatus.OPEN;
        this.id = id;
        this.name = name;
        this.description = description;
        this.callsign = callsign;
        this.ownerName = ownerName;
        this.ownerCallsign = ownerCallsign;
        this.organizationName = organizationName;
        this.organizationCallsign = organizationCallsign;
        this.contactName = contactName;
        this.contactCallsign = contactCallsign;
        this.longitude = longitude;
        this.latitude = latitude;
        this.symbolCode = symbolCode;
        this.symbolId = symbolId;
        if (regions != null) regions.forEach(this::addRegion);
    }

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }

    public void addRegion(WelcomeRegion region) {
        region.setWelcomeCenter(this);
        regions.add(region);
    }

    public void removeRegion(WelcomeRegion region) {
        regions.remove(region);
        region.setWelcomeCenter(null);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String v) {
        callsign = v;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String v) {
        ownerName = v;
    }

    public String getOwnerCallsign() {
        return ownerCallsign;
    }

    public void setOwnerCallsign(String v) {
        ownerCallsign = v;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String v) {
        organizationName = v;
    }

    public String getOrganizationCallsign() {
        return organizationCallsign;
    }

    public void setOrganizationCallsign(String v) {
        organizationCallsign = v;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String v) {
        contactName = v;
    }

    public String getContactCallsign() {
        return contactCallsign;
    }

    public void setContactCallsign(String v) {
        contactCallsign = v;
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

    public String getSymbolCode() {
        return symbolCode;
    }

    public void setSymbolCode(String v) {
        symbolCode = v;
    }

    public String getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(String v) {
        symbolId = v;
    }

    public List<WelcomeRegion> getRegions() {
        return regions;
    }
}
