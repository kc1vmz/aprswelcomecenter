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
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

@Embeddable
public class PolicyAutomation {
    private String timeZone;
    private LocalDateTime scheduledAt;
    private String recurrence;

    @Column(name = "schedule_hour")
    private Integer hour;

    @Column(name = "schedule_minute")
    private Integer minute;

    private String shriekCode;

    protected PolicyAutomation() {}

    public PolicyAutomation(
            String timeZone,
            LocalDateTime scheduledAt,
            String recurrence,
            Integer hour,
            Integer minute,
            String shriekCode) {
        this.timeZone = timeZone;
        this.scheduledAt = scheduledAt;
        this.recurrence = recurrence;
        this.hour = hour;
        this.minute = minute;
        this.shriekCode = shriekCode;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String value) {
        timeZone = value;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime value) {
        scheduledAt = value;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(String value) {
        recurrence = value;
    }

    public Integer getHour() {
        return hour;
    }

    public void setHour(Integer value) {
        hour = value;
    }

    public Integer getMinute() {
        return minute;
    }

    public void setMinute(Integer value) {
        minute = value;
    }

    public String getShriekCode() {
        return shriekCode;
    }

    public void setShriekCode(String value) {
        shriekCode = value;
    }
}
