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

import static org.assertj.core.api.Assertions.*;

import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.enumeration.PacketType;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import com.kc1vmz.aprswc.object.PolicyAutomation;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.parser.PacketParser;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PolicyScheduleTest {
    private CommunicationPolicy recurring(String interval, int hour, int minute) {
        return new CommunicationPolicy(
                null,
                null,
                null,
                "Welcome",
                null,
                null,
                MessageType.BULLETIN,
                CommunicationEventType.SCHEDULED_RECURRING,
                0,
                new PolicyAutomation("America/New_York", null, interval, hour, minute, null),
                null);
    }

    @Test
    void dailySkipsSpringGapAndFallRepeat() {
        var p = recurring("DAILY", 2, 30);
        assertThat(PolicySchedule.next(p, Instant.parse("2026-03-08T06:59:00Z")))
                .isEqualTo(Instant.parse("2026-03-09T06:30:00Z"));
        p.getAutomation().setHour(1);
        assertThat(PolicySchedule.next(p, Instant.parse("2026-11-01T05:30:00Z")))
                .isEqualTo(Instant.parse("2026-11-02T06:30:00Z"));
        p.getAutomation().setRecurrence("HOURLY");
        assertThat(PolicySchedule.next(p, Instant.parse("2026-11-01T05:30:00Z")))
                .isEqualTo(Instant.parse("2026-11-01T07:30:00Z"));
    }

    @Test
    void validatesCodesSchedulesAndForcesDeliveryType() {
        var p = recurring("DAILY", 8, 0);
        p.setCommunicationEventType(CommunicationEventType.SHRIEK_HEARD);
        p.getAutomation().setShriekCode("!1234567890!");
        PolicySchedule.validate(p, null, Instant.now());
        assertThat(p.getMessageType()).isEqualTo(MessageType.MESSAGE);
        for (String code : new String[] {"!!", "!12345678901!", "WC1", "!W!C!"}) {
            p.getAutomation().setShriekCode(code);
            assertThatThrownBy(() -> PolicySchedule.validate(p, null, Instant.now()))
                    .isInstanceOf(ResponseStatusException.class);
        }
        p.setCommunicationEventType(CommunicationEventType.SCHEDULED_ONCE);
        p.getAutomation().setScheduledAt(LocalDateTime.parse("2026-03-08T02:30:00"));
        assertThatThrownBy(() -> PolicySchedule.validate(p, null, Instant.parse("2026-01-01T00:00:00Z")))
                .hasMessageContaining("does not exist");
        p.getAutomation().setScheduledAt(LocalDateTime.parse("2026-03-09T08:30:00"));
        PolicySchedule.validate(p, null, Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(p.getMessageType()).isEqualTo(MessageType.BULLETIN);
        assertThat(PolicySchedule.next(p, Instant.parse("2026-01-01T00:00:00Z")))
                .isEqualTo(Instant.parse("2026-03-09T12:30:00Z"));
    }

    @Test
    void recognizesOnlyStatusPacketsWithOrWithoutPath() {
        for (String header : new String[] {"N1ABC>APRS:", "N1ABC>APRS,WIDE1-1:"}) {
            var p = new StationPacket(null, null, null, null, null, null);
            p.setCommand(header + ">!WC1! hello");
            assertThat(new PacketParser().parse(p)).isEqualTo(PacketType.STATUS);
            assertThat(p.getCommand()).isEqualTo(">!WC1! hello");
            assertThat(p.getCallsign()).isEqualTo("N1ABC");
        }
    }
}
