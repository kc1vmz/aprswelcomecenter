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
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "welcome_center_weather_reports")
public class WelcomeCenterWeatherReport {
    @Id
    private UUID id;

    private String callsign;

    private String longitude;
    private String latitude;
    private Float temperature;
    private Float humidity;

    @Column(name = "barometric_pressure")
    private Float barometricPressure;

    private Float luminosity;

    @Column(name = "report_time")
    private LocalDateTime reportTime;

    /** Required only by JPA. */
    protected WelcomeCenterWeatherReport() {}

    public WelcomeCenterWeatherReport(
            UUID id,
            String callsign,
            String longitude,
            String latitude,
            Float temperature,
            Float humidity,
            Float barometricPressure,
            Float luminosity,
            LocalDateTime reportTime) {
        this.id = id;
        this.callsign = callsign;
        this.longitude = longitude;
        this.latitude = latitude;
        this.temperature = temperature;
        this.humidity = humidity;
        this.barometricPressure = barometricPressure;
        this.luminosity = luminosity;
        this.reportTime = reportTime;
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

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Float getHumidity() {
        return humidity;
    }

    public void setHumidity(Float humidity) {
        this.humidity = humidity;
    }

    public Float getBarometricPressure() {
        return barometricPressure;
    }

    public void setBarometricPressure(Float barometricPressure) {
        this.barometricPressure = barometricPressure;
    }

    public Float getLuminosity() {
        return luminosity;
    }

    public void setLuminosity(Float luminosity) {
        this.luminosity = luminosity;
    }

    public LocalDateTime getReportTime() {
        return reportTime;
    }

    public void setReportTime(LocalDateTime reportTime) {
        this.reportTime = reportTime;
    }
}
