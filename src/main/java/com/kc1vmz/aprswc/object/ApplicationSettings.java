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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "application_settings")
public class ApplicationSettings {
    @Id
    private UUID id;

    private boolean usingInternetServer;
    private boolean usingKISS;
    private String internetServerAddress;
    private String internetServerUsername;
    private String internetServerPasscode;
    private String internetServerPort;
    private String kissHost;
    private String kissPort;
    private String kissBaudRate;
    private String kissInitCommand1;
    private String kissInitCommand2;
    private String digiPath;
    private String mapTileUrl;

    @Column(name = "aprs_filter")
    private String filter;

    /** Required only by JPA. */
    protected ApplicationSettings() {}

    public ApplicationSettings(
            UUID id,
            boolean usingInternetServer,
            boolean usingKISS,
            String internetServerAddress,
            String internetServerUsername,
            String internetServerPasscode,
            String internetServerPort,
            String kissHost,
            String kissPort,
            String kissBaudRate,
            String kissInitCommand1,
            String kissInitCommand2,
            String digiPath,
            String filter,
            String mapTileUrl) {
        this.id = id;
        this.usingInternetServer = usingInternetServer;
        this.usingKISS = usingKISS;
        this.internetServerAddress = internetServerAddress;
        this.internetServerUsername = internetServerUsername;
        this.internetServerPasscode = internetServerPasscode;
        this.internetServerPort = internetServerPort;
        this.kissHost = kissHost;
        this.kissPort = kissPort;
        this.kissBaudRate = kissBaudRate;
        this.kissInitCommand1 = kissInitCommand1;
        this.kissInitCommand2 = kissInitCommand2;
        this.digiPath = digiPath;
        this.filter = filter;
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

    public boolean isUsingInternetServer() {
        return usingInternetServer;
    }

    public void setUsingInternetServer(boolean value) {
        usingInternetServer = value;
    }

    public boolean isUsingKISS() {
        return usingKISS;
    }

    public void setUsingKISS(boolean value) {
        usingKISS = value;
    }

    public String getInternetServerAddress() {
        return internetServerAddress;
    }

    public void setInternetServerAddress(String value) {
        internetServerAddress = value;
    }

    public String getInternetServerUsername() {
        return internetServerUsername;
    }

    public void setInternetServerUsername(String value) {
        internetServerUsername = value;
    }

    public String getInternetServerPasscode() {
        return internetServerPasscode;
    }

    public void setInternetServerPasscode(String value) {
        internetServerPasscode = value;
    }

    public String getInternetServerPort() {
        return internetServerPort;
    }

    public void setInternetServerPort(String value) {
        internetServerPort = value;
    }

    public String getKissHost() {
        return kissHost;
    }

    public void setKissHost(String value) {
        kissHost = value;
    }

    public String getKissPort() {
        return kissPort;
    }

    public void setKissPort(String value) {
        kissPort = value;
    }

    public String getKissBaudRate() {
        return kissBaudRate;
    }

    public void setKissBaudRate(String value) {
        kissBaudRate = value;
    }

    public String getKissInitCommand1() {
        return kissInitCommand1;
    }

    public void setKissInitCommand1(String value) {
        kissInitCommand1 = value;
    }

    public String getKissInitCommand2() {
        return kissInitCommand2;
    }

    public void setKissInitCommand2(String value) {
        kissInitCommand2 = value;
    }

    public String getDigiPath() {
        return digiPath;
    }

    public void setDigiPath(String value) {
        digiPath = value;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String value) {
        filter = value;
    }

    public String getMapTileUrl() {
        return mapTileUrl;
    }

    public void setMapTileUrl(String value) {
        mapTileUrl = value;
    }
}
