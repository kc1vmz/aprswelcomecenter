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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kc1vmz.aprswc.accessor.CommunicationPolicyAccessor;
import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.object.CommunicationEvent;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CommunicationEventProcessorTest {
    @Mock
    private CommunicationPolicyAccessor communicationPolicyAccessor;

    @Spy
    @InjectMocks
    private CommunicationEventProcessor processor;

    @Test
    void processEventFindsEnterRegionPolicyUsingContextId() {
        UUID policyId = UUID.randomUUID();
        CommunicationPolicy policy = mock(CommunicationPolicy.class);
        CommunicationEvent event = new CommunicationEvent(
                UUID.randomUUID(), null, CommunicationEventType.ENTER_REGION, policyId, null, null);
        when(communicationPolicyAccessor.findById(policyId)).thenReturn(Mono.just(policy));
        doNothing().when(processor).processCommunicationPolicy(event, policy);

        processor.processEvent(event);

        verify(communicationPolicyAccessor).findById(policyId);
        verify(processor).processCommunicationPolicy(event, policy);
    }

    @Test
    void processEventFindsExitRegionPolicyUsingContextId() {
        UUID policyId = UUID.randomUUID();
        CommunicationPolicy policy = mock(CommunicationPolicy.class);
        CommunicationEvent event = new CommunicationEvent(
                UUID.randomUUID(), null, CommunicationEventType.EXIT_REGION, policyId, null, null);
        when(communicationPolicyAccessor.findById(policyId)).thenReturn(Mono.just(policy));
        doNothing().when(processor).processCommunicationPolicy(event, policy);

        processor.processEvent(event);

        verify(communicationPolicyAccessor).findById(policyId);
        verify(processor).processCommunicationPolicy(event, policy);
    }
}
