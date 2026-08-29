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

import java.time.LocalDateTime;
import java.util.UUID;

public class WelcomeCenterWeatherSummary {
    private UUID welcomeCenterId;
    private Float temperature;
    private Float humidity;
    private Float barometricPressure;
    private Float luminosity;
    private LocalDateTime reportTime;

    public WelcomeCenterWeatherSummary(
            UUID welcomeCenterId,
            Float temperature,
            Float humidity,
            Float barometricPressure,
            Float luminosity,
            LocalDateTime reportTime) {
        this.welcomeCenterId = welcomeCenterId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.barometricPressure = barometricPressure;
        this.luminosity = luminosity;
        this.reportTime = reportTime;
    }

    public UUID getWelcomeCenterId() {
        return welcomeCenterId;
    }

    public void setWelcomeCenterId(UUID welcomeCenterId) {
        this.welcomeCenterId = welcomeCenterId;
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
