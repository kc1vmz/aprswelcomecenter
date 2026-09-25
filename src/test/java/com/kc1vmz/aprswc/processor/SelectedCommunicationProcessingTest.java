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
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.accessor.*;
import com.kc1vmz.aprswc.communication.CommunicationInstanceManager;
import com.kc1vmz.aprswc.enumeration.*;
import com.kc1vmz.aprswc.object.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.*;

class SelectedCommunicationProcessingTest {
    private WelcomeCenter center(UUID allowed) {
        var center = new WelcomeCenter(
                UUID.randomUUID(),
                "Center",
                null,
                "N1TEST",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "c",
                "/",
                List.of());
        center.setCommunicationMode("SELECTED");
        center.setCommunicationInstanceIds(Set.of(allowed));
        return center;
    }

    @Test
    void excludedIncomingCommandsDoNotAcknowledgeRejectOrExecuteButIncludedCommandsCarryTheirCenter() {
        var allowed = UUID.randomUUID();
        var denied = UUID.randomUUID();
        var center = center(allowed);
        var processor = new StationPacketProcessor();
        var centers = mock(WelcomeCenterAccessor.class);
        var messages = mock(StationMessageQueue.class);
        var commands = mock(StationCommandQueue.class);
        var recognizer = mock(StationCommandProcessor.class);
        ReflectionTestUtils.setField(processor, "welcomeCenterAccessor", centers);
        ReflectionTestUtils.setField(processor, "stationMessageQueue", messages);
        ReflectionTestUtils.setField(processor, "stationCommandQueue", commands);
        ReflectionTestUtils.setField(processor, "stationCommandProcessor", recognizer);
        when(centers.findOpenByCallsign("N1TEST")).thenReturn(Mono.just(center));
        for (var text : List.of("HELP", "INVALID")) {
            var packet = new StationPacket(
                    UUID.randomUUID(),
                    denied.toString(),
                    "N2TEST",
                    LocalDateTime.now(),
                    ":N1TEST   :" + text + "{1",
                    null);
            ReflectionTestUtils.invokeMethod(processor, "processMessagePacket", packet, null);
        }
        verifyNoInteractions(messages, commands, recognizer);
        when(recognizer.isRecognizedCommand(anyString())).thenReturn(true);
        var packet = new StationPacket(
                UUID.randomUUID(), allowed.toString(), "N2TEST", LocalDateTime.now(), ":N1TEST   :HELP{1", null);
        ReflectionTestUtils.invokeMethod(processor, "processMessagePacket", packet, null);
        var ack = ArgumentCaptor.forClass(StationMessage.class);
        verify(messages).offer(ack.capture());
        assertThat(ack.getValue().getWelcomeCenter().getId()).isEqualTo(center.getId());
        assertThat(ack.getValue().isRequiresOpenCenter()).isTrue();
        verify(commands).offer(any());
    }

    @Test
    void excludedEventsAndQueuedCommandsDoNotProduceMessagesOrCommandSideEffects() {
        var allowed = UUID.randomUUID();
        var denied = UUID.randomUUID();
        var center = center(allowed);
        var packets = new StationPacketProcessor();
        var policies = mock(CommunicationPolicyAccessor.class);
        var events = mock(CommunicationEventQueue.class);
        ReflectionTestUtils.setField(packets, "communicationPolicyAccessor", policies);
        ReflectionTestUtils.setField(packets, "communicationEventQueue", events);
        packets.triggerEnterEvents(center, new Station(null, "N2TEST", null, List.of()), denied.toString());
        packets.triggerExitEvents(center, new Station(null, "N2TEST", null, List.of()), denied.toString());
        verifyNoInteractions(policies, events);
        var centers = mock(WelcomeCenterAccessor.class);
        when(centers.findOpenById(center.getId())).thenReturn(Mono.just(center));
        var messages = mock(StationMessageQueue.class);
        var commands = new StationCommandProcessor();
        var ignores = mock(IgnoreStationAccessor.class);
        ReflectionTestUtils.setField(commands, "welcomeCenterAccessor", centers);
        ReflectionTestUtils.setField(commands, "stationMessageQueue", messages);
        ReflectionTestUtils.setField(commands, "ignoreStationAccessor", ignores);
        for (var text : List.of("HELP", "START", "STOP")) {
            var command = new StationCommand(null, "N2TEST", center, null, text, StationCommandType.UNKNOWN);
            command.setPacketProcessorId(denied.toString());
            commands.processCommand(command);
        }
        var eventProcessor = new CommunicationEventProcessor();
        ReflectionTestUtils.setField(eventProcessor, "welcomeCenterAccessor", centers);
        ReflectionTestUtils.setField(eventProcessor, "stationMessageQueue", messages);
        var policy = new CommunicationPolicy(
                UUID.randomUUID(),
                null,
                center,
                "Welcome",
                null,
                null,
                MessageType.MESSAGE,
                CommunicationEventType.ENTER_REGION);
        eventProcessor.processCommunicationPolicy(
                new CommunicationEvent(
                        null, center, CommunicationEventType.ENTER_REGION, policy.getId(), "N2TEST", denied.toString()),
                policy);
        verifyNoInteractions(messages, ignores);
    }

    @Test
    void unassignedDirectedMessageUsesNewestEligibleHistoryAndRechecksWhenWriting() {
        var allowed = UUID.randomUUID();
        var denied = UUID.randomUUID();
        var center = center(allowed);
        var processor = new StationMessageProcessor();
        var manager = mock(CommunicationInstanceManager.class);
        var packets = mock(StationPacketAccessor.class);
        ReflectionTestUtils.setField(processor, "communications", manager);
        ReflectionTestUtils.setField(processor, "stationPacketAccessor", packets);
        // An explicitly supplied source callsign also identifies its Welcome Center when the message has no parent
        // field.
        when(manager.centerIdForSender("N1TEST")).thenReturn(center.getId());
        when(manager.isEligible(center.getId(), allowed.toString())).thenReturn(true);
        when(packets.findAllByCallsign("N2TEST"))
                .thenReturn(Flux.just(
                        new StationPacket(null, denied.toString(), "N2TEST", LocalDateTime.now(), "", null),
                        new StationPacket(
                                null,
                                allowed.toString(),
                                "N2TEST",
                                LocalDateTime.now().minusHours(1),
                                "",
                                null)));
        var message = new StationMessage(null, "N2TEST", "N1TEST", null, null, "Hello", null, MessageType.MESSAGE);
        processor.processMessage(message);
        var guard = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(manager)
                .sendMessage(eq(allowed.toString()), eq("N1TEST"), eq("N2TEST"), eq("Hello"), guard.capture(), any());
        assertThat(guard.getValue().getAsBoolean()).isTrue();
        when(manager.isEligible(center.getId(), allowed.toString())).thenReturn(false);
        assertThat(guard.getValue().getAsBoolean()).isFalse();
    }
}
