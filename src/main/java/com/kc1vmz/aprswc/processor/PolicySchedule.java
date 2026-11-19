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
package com.kc1vmz.aprswc.processor;

import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.enumeration.PolicyContentSource;
import com.kc1vmz.aprswc.exceptions.ApiFailure;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import com.kc1vmz.aprswc.object.PolicyAutomation;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PolicySchedule {
    private PolicySchedule() {}

    public static boolean automated(CommunicationPolicy p) {
        return p.getCommunicationEventType() == CommunicationEventType.SCHEDULED_ONCE
                || p.getCommunicationEventType() == CommunicationEventType.SCHEDULED_RECURRING
                || p.getCommunicationEventType() == CommunicationEventType.SHRIEK_HEARD;
    }

    public static boolean sameSchedule(CommunicationPolicy a, CommunicationPolicy b) {
        if (a.getCommunicationEventType() != b.getCommunicationEventType()) return false;
        PolicyAutomation x = a.getAutomation(), y = b.getAutomation();
        if (x == null || y == null) return x == y;
        return Objects.equals(x.getTimeZone(), y.getTimeZone())
                && Objects.equals(x.getScheduledAt(), y.getScheduledAt())
                && Objects.equals(x.getRecurrence(), y.getRecurrence())
                && Objects.equals(x.getHour(), y.getHour())
                && Objects.equals(x.getMinute(), y.getMinute());
    }

    public static void validate(CommunicationPolicy p, CommunicationPolicy old, Instant now) {
        if (p.getCommunicationEventType() == null)
            throw new ApiFailure(
                    HttpStatus.BAD_REQUEST,
                    "WHEN_COMMUNICATION_OCCURS_IS_REQUIRED_71B23F",
                    "When communication occurs is required");
        PolicyTransmission.configure(p);
        if (!automated(p)) {
            p.setAutomation(null);
            return;
        }
        var a = p.getAutomation();
        if (a == null)
            throw new ApiFailure(
                    HttpStatus.BAD_REQUEST,
                    "SCHEDULE_OR_SHRIEK_CONFIGURATION_IS_REQUIRED_1C5A01",
                    "Schedule or shriek configuration is required");
        try {
            ZoneId.of(a.getTimeZone());
        } catch (DateTimeException | NullPointerException e) {
            throw new ApiFailure(HttpStatus.BAD_REQUEST, "CHOOSE_A_VALID_TIME_ZONE_287EF5", "Choose a valid time zone");
        }
        if (p.getContentSource() != PolicyContentSource.TINYTOPICS)
            PolicyTransmission.validateText(p, p.getMessageText());
        if (p.getCommunicationEventType() == CommunicationEventType.SHRIEK_HEARD) {
            String code = a.getShriekCode();
            if (code == null || !code.matches("![\\x20\\x22-\\x7e]{1,10}!"))
                throw new ApiFailure(
                        HttpStatus.BAD_REQUEST,
                        "SHRIEK_CODE_REQUIRES_1_10_PRINTABLE_CHARACTER_F82D50",
                        "Shriek code requires 1-10 printable characters inside !, without embedded !");
            a.setScheduledAt(null);
            a.setRecurrence(null);
            a.setHour(null);
            a.setMinute(null);
            p.setMessageType(MessageType.MESSAGE);
        } else {
            a.setShriekCode(null);

            if (p.getCommunicationEventType() == CommunicationEventType.SCHEDULED_ONCE) {
                if (a.getScheduledAt() == null)
                    throw new ApiFailure(
                            HttpStatus.BAD_REQUEST,
                            "SCHEDULED_DATE_AND_TIME_ARE_REQUIRED_D2F3A0",
                            "Scheduled date and time are required");
                a.setScheduledAt(a.getScheduledAt().withSecond(0).withNano(0));
                a.setRecurrence(null);
                a.setHour(null);
                a.setMinute(null);
                Instant target = instant(a.getScheduledAt(), ZoneId.of(a.getTimeZone()));
                if (target == null)
                    throw new ApiFailure(
                            HttpStatus.BAD_REQUEST,
                            "THIS_LOCAL_TIME_DOES_NOT_EXIST_DUE_TO_A_TIME__CC864B",
                            "This local time does not exist due to a time-zone clock change");
                if ((old == null || !sameSchedule(old, p)) && !target.isAfter(now))
                    throw new ApiFailure(
                            HttpStatus.BAD_REQUEST,
                            "ONE_TIME_SCHEDULE_MUST_BE_IN_THE_FUTURE_5DD08C",
                            "One-time schedule must be in the future");
            } else {
                a.setScheduledAt(null);
                if (!"HOURLY".equals(a.getRecurrence()) && !"DAILY".equals(a.getRecurrence()))
                    throw new ApiFailure(
                            HttpStatus.BAD_REQUEST,
                            "RECURRENCE_MUST_BE_HOURLY_OR_DAILY_B1D5F5",
                            "Recurrence must be HOURLY or DAILY");
                if (a.getMinute() == null || a.getMinute() < 0 || a.getMinute() > 59)
                    throw new ApiFailure(HttpStatus.BAD_REQUEST, "MINUTE_MUST_BE_0_59_050A43", "Minute must be 0-59");
                if ("DAILY".equals(a.getRecurrence()) && (a.getHour() == null || a.getHour() < 0 || a.getHour() > 23))
                    throw new ApiFailure(HttpStatus.BAD_REQUEST, "HOUR_MUST_BE_0_23_1AE058", "Hour must be 0-23");
                if ("HOURLY".equals(a.getRecurrence())) a.setHour(null);
            }
        }
    }
    // Select only the earlier occurrence of ambiguous local times; skip gaps.
    private static Instant instant(LocalDateTime local, ZoneId zone) {
        var offsets = zone.getRules().getValidOffsets(local);
        return offsets.isEmpty() ? null : local.toInstant(offsets.getFirst());
    }

    public static Instant next(CommunicationPolicy p, Instant after) {
        var a = p.getAutomation();
        if (!automated(p) || p.getCommunicationEventType() == CommunicationEventType.SHRIEK_HEARD) return null;
        ZoneId zone = ZoneId.of(a.getTimeZone());
        if (p.getCommunicationEventType() == CommunicationEventType.SCHEDULED_ONCE) {
            Instant candidate = instant(a.getScheduledAt(), zone);
            return candidate != null && candidate.isAfter(after) ? candidate : null;
        }
        var date = after.atZone(zone).toLocalDate();
        for (int day = 0; day < 4; day++) {
            for (int hour = 0; hour < 24; hour++) {
                if ("DAILY".equals(a.getRecurrence()) && hour != a.getHour()) continue;
                Instant candidate = instant(date.plusDays(day).atTime(hour, a.getMinute()), zone);
                if (candidate != null && candidate.isAfter(after)) return candidate;
            }
        }
        throw new IllegalStateException("Unable to calculate next occurrence");
    }

    private static ResponseStatusException invalid(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
