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
package com.kc1vmz.aprswc.communication;

import static org.assertj.core.api.Assertions.*;

import com.kc1vmz.aprswc.utils.APRSTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class AprsMessageProtocolTest {
    @Test
    void parsesRadioLineEndingsAndReplyAcksWithoutChangingMessageSpaces() {
        assertThat(AprsMessageProtocol.identifier("v{18\r")).isEqualTo("18");
        assertThat(AprsMessageProtocol.control("ack6E\r\n").wireText()).isEqualTo("ack6E");
        assertThat(AprsMessageProtocol.control("rej6E\r").kind()).isEqualTo("rej");
        assertThat(AprsMessageProtocol.control("ack18\r{69").wireText()).isEqualTo("ack18");
        assertThat(AprsMessageProtocol.identifier("v{18}6E\r")).isEqualTo("18");
        assertThat(AprsMessageProtocol.replyAcknowledgement("v{18}6E\r")).isEqualTo("6E");
        assertThat(AprsMessageProtocol.body(" v {18\r\n")).isEqualTo(" v ");
        assertThat(AprsMessageProtocol.normalize("a\u001cb\r")).isEqualTo("a\u001cb");
        for (String invalid : new String[] {"v{123456\r", "v{A B\r", "v{\r"})
            assertThat(AprsMessageProtocol.identifier(invalid)).isNull();
    }

    @Test
    void objectTimestampsAreUtcIncludingDateRollover() {
        assertThat(APRSTime.convertZonedDateTimeToDDHHMM(ZonedDateTime.parse("2026-11-07T05:09:00-05:00")))
                .isEqualTo("071009z");
        assertThat(APRSTime.convertZonedDateTimeToDDHHMM(ZonedDateTime.parse("2026-11-30T23:59:00-05:00")))
                .isEqualTo("010459z");
    }
}
