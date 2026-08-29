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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kc1vmz.aprswc.accessor.CommunicationPolicyAccessor;
import com.kc1vmz.aprswc.accessor.StationAccessor;
import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.DistanceUnit;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.enumeration.StationCommandType;
import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.CommunicationCategory;
import com.kc1vmz.aprswc.object.CommunicationEvent;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationCommand;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.parser.LocationPacketParser;
import com.kc1vmz.aprswc.parser.MicEPacketParser;
import com.kc1vmz.aprswc.parser.WeatherPacketParser;
import com.kc1vmz.aprswc.utils.GeoFenceUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class StationPacketProcessorTest {
    @Mock
    private StationCommandQueue stationCommandQueue;

    @Mock
    private WelcomeCenterAccessor welcomeCenterAccessor;

    @Mock
    private WeatherPacketParser weatherPacketParser;

    @Mock
    private MicEPacketParser micEPacketParser;

    @Mock
    private LocationPacketParser locationPacketParser;

    @Mock
    private StationAccessor stationAccessor;

    @Mock
    private GeoFenceUtils geoFenceUtils;

    @Mock
    private CommunicationPolicyAccessor communicationPolicyAccessor;

    @Mock
    private CommunicationEventQueue communicationEventQueue;

    @InjectMocks
    private StationPacketProcessor processor;

    @Test
    void convertsWelcomeCenterPacketToQueuedCommand() {
        LocalDateTime receivedTime = LocalDateTime.of(2026, 8, 22, 10, 30);
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", receivedTime, " subscribe ", null);
        packet.setCallsignTo("KC1VMZ");
        WelcomeCenter welcomeCenter = new WelcomeCenter(
                UUID.randomUUID(),
                "Center",
                null,
                "KC1VMZ",
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
        when(welcomeCenterAccessor.findByCallsign("KC1VMZ"))
                .thenReturn(reactor.core.publisher.Mono.just(welcomeCenter));

        processor.processWelcomeCenterCommand(packet);

        ArgumentCaptor<StationCommand> commandCaptor = ArgumentCaptor.forClass(StationCommand.class);
        verify(stationCommandQueue).offer(commandCaptor.capture());
        StationCommand command = commandCaptor.getValue();
        assertAll(
                () -> assertNotNull(command.getId()),
                () -> assertEquals("N1ABC", command.getCallsign()),
                () -> assertEquals(welcomeCenter, command.getWelcomeCenter()),
                () -> assertEquals(receivedTime, command.getReceivedTime()),
                () -> assertEquals(" subscribe ", command.getCommand()),
                () -> assertEquals(StationCommandType.UNKNOWN, command.getType()));
    }

    @Test
    void delegatesWeatherPacketParsing() {
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "weather", null);
        Station station = new Station(UUID.randomUUID(), "N1ABC", StationState.UNKNOWN, List.of());

        processor.processWeatherPacket(packet, station);

        verify(weatherPacketParser).parseWeatherPacket(packet);
    }

    @Test
    void delegatesMicEPacketParsing() {
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "mic-e", null);
        Station station = new Station(UUID.randomUUID(), "N1ABC", StationState.UNKNOWN, List.of());

        processor.processMicEPacket(packet, station);

        verify(micEPacketParser).parseMicEPacket(packet, station);
    }

    @Test
    void delegatesLocationPacketParsing() {
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "location", null);
        Station station = new Station(UUID.randomUUID(), "N1ABC", StationState.UNKNOWN, List.of());

        processor.determineStationPosition(packet, station);

        verify(locationPacketParser).parseLocationPacket(packet, station);
    }

    @Test
    void loadsMostRecentStationPositions() {
        Station station = new Station(UUID.randomUUID(), "N1ABC", StationState.UNKNOWN, List.of());
        StationPosition position =
                new StationPosition(UUID.randomUUID(), "NOCALL-1", "07106.00W", "4218.00N", LocalDateTime.now());
        when(stationAccessor.findPositions(station.getId(), 5)).thenReturn(Flux.just(position));

        List<StationPosition> result = processor.determineLastStationPositions(station);

        assertEquals(List.of(position), result);
        verify(stationAccessor).findPositions(station.getId(), 5);
    }

    @Test
    void determinesStoppedPositionsUsingFiftyFootGeofence() {
        StationPosition positionFirst =
                new StationPosition(UUID.randomUUID(), "NOCALL-1", "07106.00W", "4218.00N", LocalDateTime.now());
        StationPosition positionSecond =
                new StationPosition(UUID.randomUUID(), "NOCALL-1", "07106.01W", "4218.01N", LocalDateTime.now());
        when(geoFenceUtils.isInGeofenceCircle(
                        positionFirst.getLongitude(),
                        positionFirst.getLatitude(),
                        50,
                        DistanceUnit.FEET,
                        positionSecond))
                .thenReturn(true);

        assertTrue(processor.essentiallyStopped(positionFirst, positionSecond));
        verify(geoFenceUtils).isInGeofenceCircle("07106.00W", "4218.00N", 50, DistanceUnit.FEET, positionSecond);
    }

    @Test
    void loadsWelcomeCenterPoliciesForEnterAndExitEvents() {
        WelcomeCenter welcomeCenter = new WelcomeCenter(
                UUID.randomUUID(),
                "Center",
                null,
                "KC1VMZ",
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
        Station station = new Station(UUID.randomUUID(), "N1ABC", StationState.UNKNOWN, List.of());
        String packetProcessorId = "packet-processor-id-1";
        when(communicationPolicyAccessor.findByWelcomeCenterId(welcomeCenter.getId()))
                .thenReturn(Flux.empty());

        processor.triggerEnterEvents(welcomeCenter, station, packetProcessorId);
        processor.triggerExitEvents(welcomeCenter, station, packetProcessorId);

        verify(communicationPolicyAccessor, org.mockito.Mockito.times(2)).findByWelcomeCenterId(welcomeCenter.getId());
    }

    @Test
    void queuesCommunicationEventForMatchingPolicy() {
        WelcomeCenter welcomeCenter = new WelcomeCenter(
                UUID.randomUUID(),
                "Center",
                null,
                "KC1VMZ",
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
        Station station = new Station(UUID.randomUUID(), "N1ABC", StationState.UNKNOWN, List.of());
        CommunicationCategory category = new CommunicationCategory(UUID.randomUUID(), "General", null, false, false);
        String packetProcessorId = "packet-processor-id-1";
        CommunicationPolicy policy = new CommunicationPolicy(
                UUID.randomUUID(),
                category,
                welcomeCenter,
                "Welcome",
                null,
                null,
                MessageType.MESSAGE,
                CommunicationEventType.ENTER_REGION);
        when(communicationPolicyAccessor.findByWelcomeCenterId(welcomeCenter.getId()))
                .thenReturn(Flux.just(policy));

        processor.triggerEvents(welcomeCenter, station, CommunicationEventType.ENTER_REGION, packetProcessorId);

        ArgumentCaptor<CommunicationEvent> eventCaptor = ArgumentCaptor.forClass(CommunicationEvent.class);
        verify(communicationEventQueue).offer(eventCaptor.capture());
        CommunicationEvent event = eventCaptor.getValue();
        assertAll(
                () -> assertNotNull(event.getId()),
                () -> assertEquals(welcomeCenter, event.getWelcomeCenter()),
                () -> assertEquals(CommunicationEventType.ENTER_REGION, event.getType()),
                () -> assertEquals(policy.getId(), event.getContext()));
    }
}
