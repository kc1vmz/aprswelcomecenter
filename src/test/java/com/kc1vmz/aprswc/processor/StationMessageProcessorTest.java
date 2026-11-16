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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kc1vmz.aprswc.accessor.StationAccessor;
import com.kc1vmz.aprswc.accessor.StationMessageAccessor;
import com.kc1vmz.aprswc.accessor.StationPacketAccessor;
import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.communication.CommunicationInstanceManager;
import com.kc1vmz.aprswc.communication.MessageIdentifierService;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StationMessageProcessorTest {
    @Mock
    private WelcomeCenterAccessor welcomeCenterAccessor;

    @Mock
    private StationAccessor stationAccessor;

    @Mock
    private StationMessageAccessor stationMessageAccessor;

    @Mock
    private StationPacketAccessor stationPacketAccessor;

    @Mock
    private CommunicationInstanceManager communications;

    @Mock
    private MessageIdentifierService identifiers;

    @InjectMocks
    private StationMessageProcessor processor;

    @Test
    void standaloneUsesLatestConnectedRouteWithoutCenterRestrictions() {
        var message = new StationMessage(null, "N1ONE", "K4BO", null, null, "Hello", null, MessageType.MESSAGE);
        when(communications.connectedRoutes(null)).thenReturn(List.of("connected"));
        when(stationPacketAccessor.findAllByCallsign("N1ONE"))
                .thenReturn(Flux.just(
                        new StationPacket(null, "offline", "N1ONE", LocalDateTime.now(), null, null),
                        new StationPacket(
                                null, "connected", "N1ONE", LocalDateTime.now().minusMinutes(1), null, null)));
        processor.prepareStandalone(message);
        assertEquals("connected", message.getPacketProcessorId());
        processor.processMessage(message);
        verify(communications, never()).centerIdForSender(any());
        verify(communications, never()).isEligible(any(), any());
    }

    @Test
    void standaloneExplicitRouteDoesNotRequireHeardHistory() {
        var message = new StationMessage(null, "N1ONE", "K4BO", null, null, "Hello", "connected", MessageType.MESSAGE);
        when(communications.connectedRoutes(null)).thenReturn(List.of("connected"));
        processor.prepareStandalone(message);
        assertEquals("connected", message.getPacketProcessorId());
        verifyNoInteractions(stationPacketAccessor);
    }

    @Test
    void standaloneRejectsDisconnectedRoute() {
        var message = new StationMessage(null, "N1ONE", "K4BO", null, null, "Hello", "offline", MessageType.MESSAGE);
        when(communications.connectedRoutes(null)).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> processor.prepareStandalone(message));
    }

    @Test
    void sendsAckDirectlyWithoutIdentifierServiceOrRetryQueue() {
        var ack = new StationMessage(null, "N2EDX-7", "K4BO", null, null, "ack18\r", "route", MessageType.MESSAGE);
        processor.processMessage(ack);
        verify(communications).sendUntracked(eq("route"), eq("K4BO"), eq("N2EDX-7"), eq("ack18"), any(), any());
        verify(communications, never()).sendMessage(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(identifiers);
        assertNull(ack.getMessageIdentifier());
    }

    @Test
    void createsOnePersistedDirectedMessagePerStationInWelcomeCenter() {
        UUID centerId = UUID.randomUUID();
        UUID firstStationId = UUID.randomUUID();
        UUID secondStationId = UUID.randomUUID();
        WelcomeCenter center = new WelcomeCenter(
                centerId, "Center", null, "KC1VMZ", null, null, null, null, null, null, null, null, "c", "/",
                List.of());
        Station firstStation = new Station(firstStationId, "N1ONE", null, List.of());
        Station secondStation = new Station(secondStationId, "N2TWO", null, List.of());
        StationPosition firstPosition = positionFor(firstStation);
        StationPosition duplicateFirstPosition = positionFor(firstStation);
        StationPosition secondPosition = positionFor(secondStation);
        when(welcomeCenterAccessor.findStationPositions(centerId))
                .thenReturn(Flux.just(firstPosition, duplicateFirstPosition, secondPosition));
        when(stationAccessor.findById(firstStationId)).thenReturn(Mono.just(firstStation));
        when(stationAccessor.findById(secondStationId)).thenReturn(Mono.just(secondStation));
        when(stationMessageAccessor.create(any(StationMessage.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        processor.processMessage(
                new StationMessage(null, null, "KC1VMZ", center, null, "Welcome", null, MessageType.MESSAGE));

        ArgumentCaptor<StationMessage> messages = ArgumentCaptor.forClass(StationMessage.class);
        verify(stationMessageAccessor, times(2)).create(messages.capture());
        assertEquals(
                List.of("N1ONE", "N2TWO"),
                messages.getAllValues().stream()
                        .map(StationMessage::getCallsignTo)
                        .toList());
        messages.getAllValues().forEach(message -> {
            assertEquals("KC1VMZ", message.getCallsignFrom());
            assertEquals("Welcome", message.getContent());
            assertSame(center, message.getWelcomeCenter());
        });
    }

    @Test
    void persistsDirectedMessageAfterSending() {
        StationMessage message = new StationMessage(
                UUID.randomUUID(), "N1ONE", "KC1VMZ", null, null, "Hello", null, MessageType.MESSAGE);
        StationPacket packet = new StationPacket(UUID.randomUUID(), "APRS_IS", "N1ONE", null, "packet", null);
        when(stationPacketAccessor.findAllByCallsign("N1ONE")).thenReturn(Flux.just(packet));
        when(communications.sendMessage(eq("APRS_IS"), eq("KC1VMZ"), eq("N1ONE"), eq("Hello"), any(), any()))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(5).run();
                    return true;
                });
        when(stationMessageAccessor.saveProcessedMessage(message)).thenReturn(Mono.just(message));

        processor.processMessage(message);

        verify(communications).sendMessage(eq("APRS_IS"), eq("KC1VMZ"), eq("N1ONE"), eq("Hello"), any(), any());
        verify(stationMessageAccessor).saveProcessedMessage(message);
        assertEquals("APRS_IS", message.getPacketProcessorId());
        assertNotNull(message.getSentTime());
    }

    @Test
    void explicitUnavailableRouteIsDroppedWithoutFallbackOrSentTime() {
        var route = UUID.randomUUID().toString();
        var message = new StationMessage(
                UUID.randomUUID(), "N1ONE", "KC1VMZ", null, null, "Hello", route, MessageType.MESSAGE);
        processor.processMessage(message);
        verify(communications).sendMessage(eq(route), eq("KC1VMZ"), eq("N1ONE"), eq("Hello"), any(), any());
        verifyNoInteractions(stationPacketAccessor, stationMessageAccessor);
        assertNull(message.getSentTime());
    }

    private StationPosition positionFor(Station station) {
        StationPosition position = new StationPosition(null, station.getCallsign(), "1", "2", null);
        position.setStation(station);
        return position;
    }
}
