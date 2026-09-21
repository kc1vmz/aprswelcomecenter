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
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "communication_instances")
public class CommunicationInstance {
    @Id
    private UUID id;

    @Version
    private long version;

    private String type;
    private String state = "ACTIVE";
    private String host;
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passcode;

    @Column(name = "aprs_filter")
    private String filter;

    private String serialDevice;
    private String initCommand1;
    private String initCommand2;
    private String digiPath;
    private Integer port;
    private Integer baudRate;

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

    public String getType() {
        return type;
    }

    public void setType(String value) {
        type = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String value) {
        host = value;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        username = value;
    }

    public String getPasscode() {
        return passcode;
    }

    public void setPasscode(String value) {
        passcode = value;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String value) {
        filter = value;
    }

    public String getSerialDevice() {
        return serialDevice;
    }

    public void setSerialDevice(String value) {
        serialDevice = value;
    }

    public String getInitCommand1() {
        return initCommand1;
    }

    public void setInitCommand1(String value) {
        initCommand1 = value;
    }

    public String getInitCommand2() {
        return initCommand2;
    }

    public void setInitCommand2(String value) {
        initCommand2 = value;
    }

    public String getDigiPath() {
        return digiPath;
    }

    public void setDigiPath(String value) {
        digiPath = value;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer value) {
        port = value;
    }

    public Integer getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(Integer value) {
        baudRate = value;
    }

    public String getLabel() {
        return switch (type) {
            case "APRS_IS" -> "APRS-IS: " + host + ":" + port + " (" + username + ")";
            case "KISS_TCP" -> "KISS TCP: " + host + ":" + port;
            default -> "KISS Serial: " + serialDevice + " (" + baudRate + ")";
        };
    }
}
