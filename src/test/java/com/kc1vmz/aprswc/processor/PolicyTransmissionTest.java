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
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import org.junit.jupiter.api.Test;

class PolicyTransmissionTest {
    private CommunicationPolicy policy(MessageType type, String address) {
        return new CommunicationPolicy(
                null, null, null, "Text", null, null, type, CommunicationEventType.SCHEDULED_ONCE, address);
    }

    @Test
    void bulletinAddressesAllowObservedFormsAndRejectInvalidInput() {
        for (String address : new String[] {"BLN", "BLN0", "BLN9", "BLNASCACS", "BLNA-WEB", "BLND-RPT1", "BLN0WXMY"}) {
            var p = policy(MessageType.BULLETIN, " " + address.toLowerCase() + " ");
            PolicyTransmission.configure(p);
            assertThat(p.getBulletinAddress()).isEqualTo(address);
        }
        for (String address : new String[] {"BLN-", "BLN!", "BLNA123456", "BLN1A B", "BLN1:BAD", ""})
            assertThatThrownBy(() -> PolicyTransmission.configure(policy(MessageType.BULLETIN, address)))
                    .isInstanceOf(RuntimeException.class);
    }

    @Test
    void statusAndBulletinHaveDistinctLimitsAndClearUnusedAddress() {
        var status = policy(MessageType.STATUS_REPORT, "BLN1");
        PolicyTransmission.configure(status);
        assertThat(status.getBulletinAddress()).isNull();
        PolicyTransmission.validateText(status, "x".repeat(62));
        assertThatThrownBy(() -> PolicyTransmission.validateText(status, "x".repeat(63)))
                .isInstanceOf(RuntimeException.class);
        var bulletin = policy(MessageType.BULLETIN, "BLN1");
        PolicyTransmission.validateText(bulletin, "x".repeat(67));
        assertThatThrownBy(() -> PolicyTransmission.validateText(bulletin, "x".repeat(68)))
                .isInstanceOf(RuntimeException.class);
        for (String invalid : new String[] {"bad\rtext", "bad|text", "bad~text"})
            assertThatThrownBy(() -> PolicyTransmission.validateText(status, invalid))
                    .isInstanceOf(RuntimeException.class);
        status.setCommunicationEventType(CommunicationEventType.ON_REQUEST);
        PolicyTransmission.configure(status);
        assertThat(status.getMessageType()).isEqualTo(MessageType.MESSAGE);
    }
}
