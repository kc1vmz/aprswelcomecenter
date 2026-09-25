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

import com.kc1vmz.aprswc.communication.CommunicationScope;
import java.util.Set;
import java.util.UUID;

public class ObjectBeacon {
    private String objectName;
    private String callsignFrom;
    private String longitude;
    private String latitude;
    private String symbolCode;
    private String symbolId;
    private String statusMessage;
    private boolean active;
    private CommunicationScope communicationScope;
    private Set<UUID> targetInstanceIds;

    public ObjectBeacon(
            String objectName,
            String callsignFrom,
            String longitude,
            String latitude,
            String symbolCode,
            String symbolId,
            String statusMessage,
            boolean active,
            CommunicationScope communicationScope) {
        this.objectName = objectName;
        this.callsignFrom = callsignFrom;
        this.longitude = longitude;
        this.latitude = latitude;
        this.symbolCode = symbolCode;
        this.symbolId = symbolId;
        this.statusMessage = statusMessage;
        this.active = active;
        this.communicationScope = communicationScope;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String value) {
        objectName = value;
    }

    public String getCallsignFrom() {
        return callsignFrom;
    }

    public void setCallsignFrom(String value) {
        callsignFrom = value;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String value) {
        longitude = value;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String value) {
        latitude = value;
    }

    public String getSymbolCode() {
        return symbolCode;
    }

    public void setSymbolCode(String value) {
        symbolCode = value;
    }

    public String getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(String value) {
        symbolId = value;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String value) {
        statusMessage = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean value) {
        active = value;
    }

    public CommunicationScope getCommunicationScope() {
        return communicationScope;
    }

    public void setCommunicationScope(CommunicationScope value) {
        communicationScope = value;
    }

    public java.util.Set<java.util.UUID> getTargetInstanceIds() {
        return targetInstanceIds;
    }

    public void setTargetInstanceIds(java.util.Set<java.util.UUID> value) {
        targetInstanceIds = java.util.Set.copyOf(value);
    }

    private java.util.function.BooleanSupplier transmissionPermitted = () -> true;

    public void setTransmissionPermitted(java.util.function.BooleanSupplier value) {
        transmissionPermitted = value;
    }

    public boolean isTransmissionPermitted() {
        return transmissionPermitted.getAsBoolean();
    }
}
