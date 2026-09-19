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

import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.accessor.*;
import com.kc1vmz.aprswc.enumeration.*;
import com.kc1vmz.aprswc.object.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

class ClosedCenterProcessingTest {
    private WelcomeCenter center() {
        return new WelcomeCenter(
                UUID.randomUUID(),
                "Center",
                "Description",
                "KC1VMZ",
                null,
                "KC1VMZ",
                null,
                null,
                null,
                null,
                "07106.00W",
                "4218.00N",
                "c",
                "/",
                List.of());
    }

    @Test
    void periodicBeaconsOnlyForOpenCenters() {
        var processor = new WelcomeCenterObjectBeaconProcessor();
        var queue = mock(ObjectBeaconQueue.class);
        ReflectionTestUtils.setField(processor, "objectBeaconQueue", queue);
        var center = center();
        center.setStatus(WelcomeCenterStatus.CLOSED);
        processor.beaconWelcomeCenterObject(center);
        verifyNoInteractions(queue);
        center.setStatus(WelcomeCenterStatus.OPEN);
        processor.beaconWelcomeCenterObject(center);
        verify(queue).offer(any());
    }

    @Test
    void closedCentersDoNotGenerateEnterOrExitEvents() {
        var processor = new StationPacketProcessor();
        var policies = mock(CommunicationPolicyAccessor.class);
        var queue = mock(CommunicationEventQueue.class);
        ReflectionTestUtils.setField(processor, "communicationPolicyAccessor", policies);
        ReflectionTestUtils.setField(processor, "communicationEventQueue", queue);
        var center = center();
        center.setStatus(WelcomeCenterStatus.CLOSED);
        processor.triggerEnterEvents(center, null, "test");
        processor.triggerExitEvents(center, null, "test");
        verifyNoInteractions(policies, queue);
    }

    @Test
    void pendingRegionEventsRecheckCurrentStateBeforeGeneratingMessages() {
        var processor = new CommunicationEventProcessor();
        var centers = mock(WelcomeCenterAccessor.class);
        var queue = mock(StationMessageQueue.class);
        ReflectionTestUtils.setField(processor, "welcomeCenterAccessor", centers);
        ReflectionTestUtils.setField(processor, "stationMessageQueue", queue);
        var center = center(); // Stale OPEN snapshot, now closed in database.
        var policy = new CommunicationPolicy(
                UUID.randomUUID(),
                null,
                center,
                "Hello",
                null,
                null,
                MessageType.MESSAGE,
                CommunicationEventType.ENTER_REGION);
        when(centers.findOpenById(center.getId())).thenReturn(Mono.empty());
        for (var type : List.of(CommunicationEventType.ENTER_REGION, CommunicationEventType.EXIT_REGION)) {
            processor.processCommunicationPolicy(
                    new CommunicationEvent(null, center, type, policy.getId(), "N1TEST", null), policy);
        }
        verifyNoInteractions(queue);
        when(centers.findOpenById(center.getId())).thenReturn(Mono.just(center));
        processor.processCommunicationPolicy(
                new CommunicationEvent(
                        null, center, CommunicationEventType.ENTER_REGION, policy.getId(), "N1TEST", null),
                policy);
        verify(queue).offer(argThat(StationMessage::isRequiresOpenCenter));
    }

    @Test
    void closedCenterIgnoresInboundMessagesWithoutAckOrReject() {
        var processor = new StationPacketProcessor();
        var centers = mock(WelcomeCenterAccessor.class);
        var messages = mock(StationMessageQueue.class);
        var commands = mock(StationCommandQueue.class);
        ReflectionTestUtils.setField(processor, "welcomeCenterAccessor", centers);
        ReflectionTestUtils.setField(processor, "stationMessageQueue", messages);
        ReflectionTestUtils.setField(processor, "stationCommandQueue", commands);
        when(centers.findOpenByCallsign("KC1VMZ")).thenReturn(Mono.empty());
        for (String content : List.of("HELP{1", "STOP{2", "START{3", "UNKNOWN{4")) {
            StationPacket packet = new StationPacket(null, "test", "N1TEST", null, ":KC1VMZ   :" + content, null);
            ReflectionTestUtils.invokeMethod(processor, "processMessagePacket", packet, null);
        }
        verifyNoInteractions(messages, commands);
    }

    @Test
    void pendingCommandsAreDiscardedWhileClosedWithoutChangingIgnoreList() {
        var processor = new StationCommandProcessor();
        var centers = mock(WelcomeCenterAccessor.class);
        var messages = mock(StationMessageQueue.class);
        var ignores = mock(IgnoreStationAccessor.class);
        ReflectionTestUtils.setField(processor, "welcomeCenterAccessor", centers);
        ReflectionTestUtils.setField(processor, "stationMessageQueue", messages);
        ReflectionTestUtils.setField(processor, "ignoreStationAccessor", ignores);
        var center = center();
        when(centers.findOpenById(center.getId())).thenReturn(Mono.empty());
        for (String command : List.of("HELP", "INFO", "START", "STOP", "WEATHER")) {
            processor.processCommand(
                    new StationCommand(null, "N1TEST", center, null, command, StationCommandType.UNKNOWN));
        }
        verifyNoInteractions(messages, ignores);
    }

    @Test
    void outgoingAutomaticMessagesAreDiscardedIfCenterClosedSinceQueueing() {
        var processor = new StationMessageProcessor();
        var centers = mock(WelcomeCenterAccessor.class);
        var settings = mock(ApplicationSettingsAccessor.class);
        var messages = mock(StationMessageAccessor.class);
        ReflectionTestUtils.setField(processor, "welcomeCenterAccessor", centers);
        ReflectionTestUtils.setField(processor, "applicationSettingsAccessor", settings);
        ReflectionTestUtils.setField(processor, "stationMessageAccessor", messages);
        var center = center();
        when(centers.findOpenById(center.getId())).thenReturn(Mono.empty());
        var message = new StationMessage(null, "N1TEST", "KC1VMZ", center, null, "Welcome", null, MessageType.MESSAGE);
        message.setRequiresOpenCenter(true);
        processor.processMessage(message);
        verifyNoInteractions(settings, messages);
    }
}
