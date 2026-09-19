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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kc1vmz.aprswc.accessor.IgnoreStationAccessor;
import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.accessor.WelcomeCenterWeatherReportAccessor;
import com.kc1vmz.aprswc.object.IgnoreStation;
import com.kc1vmz.aprswc.object.StationCommand;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherSummary;
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
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StationCommandProcessorTest {
    @Mock
    private StationMessageQueue stationMessageQueue;

    @Mock
    private WelcomeCenterWeatherReportAccessor welcomeCenterWeatherReportAccessor;

    @Mock
    private WelcomeCenterAccessor welcomeCenterAccessor;

    @Mock
    private IgnoreStationAccessor ignoreStationAccessor;

    @InjectMocks
    private StationCommandProcessor processor;

    @Test
    void recognizesKnownCommands() {
        assertTrue(processor.isRecognizedCommand("INFO"));
        assertTrue(processor.isRecognizedCommand("WEATHER"));
        assertTrue(processor.isRecognizedCommand("VOICE"));
        assertTrue(processor.isRecognizedCommand("COMM"));
        assertTrue(processor.isRecognizedCommand("CLUBS"));
        assertTrue(processor.isRecognizedCommand("EVENTS"));
        assertTrue(processor.isRecognizedCommand("WARNINGS"));
        assertTrue(processor.isRecognizedCommand("START"));
        assertTrue(processor.isRecognizedCommand("STOP"));
        assertTrue(processor.isRecognizedCommand("OTHERS"));
        assertTrue(processor.isRecognizedCommand("HELP"));
    }

    @Test
    void rejectsUnknownAndMissingCommands() {
        assertFalse(processor.isRecognizedCommand("UNKNOWN"));
        assertFalse(processor.isRecognizedCommand("STATUS"));
        assertFalse(processor.isRecognizedCommand("IGNOREME"));
        assertFalse(processor.isRecognizedCommand(""));
        assertFalse(processor.isRecognizedCommand(null));
    }

    @Test
    void sendsWholeNumberWeatherSummary() {
        WelcomeCenter center = welcomeCenter();
        WelcomeCenterWeatherSummary summary =
                new WelcomeCenterWeatherSummary(center.getId(), 72.6f, 54.4f, 1013.2f, 815.8f, LocalDateTime.now());
        when(welcomeCenterWeatherReportAccessor.findSummary(center.getId())).thenReturn(Mono.just(summary));

        processor.processCommand(weatherCommand(center));

        ArgumentCaptor<StationMessage> message = ArgumentCaptor.forClass(StationMessage.class);
        verify(stationMessageQueue).offer(message.capture());
        assertEquals(
                "Temp: 73F Hum: 54% Bar: 1013Mb Lum: 816", message.getValue().getContent());
        assertEquals("N1ABC", message.getValue().getCallsignTo());
        assertEquals("KC1VMZ", message.getValue().getCallsignFrom());
    }

    @Test
    void sendsUnavailableMessageWhenWeatherSummaryIsMissing() {
        WelcomeCenter center = welcomeCenter();
        when(welcomeCenterWeatherReportAccessor.findSummary(center.getId())).thenReturn(Mono.empty());

        processor.processCommand(weatherCommand(center));

        ArgumentCaptor<StationMessage> message = ArgumentCaptor.forClass(StationMessage.class);
        verify(stationMessageQueue).offer(message.capture());
        assertEquals("No current weather summary available", message.getValue().getContent());
    }

    @Test
    void includesAllWelcomeCenterCallsignsInStartResponse() {
        WelcomeCenter commandCenter = welcomeCenter();
        WelcomeCenter secondCenter = new WelcomeCenter(
                UUID.randomUUID(),
                "Second",
                null,
                "N1TWO",
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
        WelcomeCenter blankCallsignCenter = new WelcomeCenter(
                UUID.randomUUID(),
                "Blank",
                null,
                "",
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
        when(welcomeCenterAccessor.findAll()).thenReturn(Flux.just(commandCenter, blankCallsignCenter, secondCenter));
        when(ignoreStationAccessor.deleteByCallsign("N1ABC")).thenReturn(Mono.empty());

        processor.processCommand(new StationCommand(UUID.randomUUID(), "N1ABC", commandCenter, null, "START", null));

        ArgumentCaptor<StationMessage> messages = ArgumentCaptor.forClass(StationMessage.class);
        verify(stationMessageQueue, times(2)).offer(messages.capture());
        assertEquals(
                List.of("Resuming messages from KC1VMZ,N1TWO", "Send STOP to stop receiving messages"),
                messages.getAllValues().stream().map(StationMessage::getContent).toList());
        verify(ignoreStationAccessor).deleteByCallsign("N1ABC");
    }

    @Test
    void addsStationToIgnoreListForStopRequest() {
        WelcomeCenter center = welcomeCenter();
        when(welcomeCenterAccessor.findAll()).thenReturn(Flux.just(center));
        when(ignoreStationAccessor.createIfAbsent("N1ABC"))
                .thenReturn(Mono.just(new IgnoreStation(UUID.randomUUID(), "N1ABC")));

        processor.processCommand(new StationCommand(UUID.randomUUID(), "N1ABC", center, null, "STOP", null));

        ArgumentCaptor<StationMessage> messages = ArgumentCaptor.forClass(StationMessage.class);
        verify(ignoreStationAccessor).createIfAbsent("N1ABC");
        verify(stationMessageQueue, times(2)).offer(messages.capture());
        assertEquals(
                List.of("Stopping messages from KC1VMZ", "Send START to resume receiving messages"),
                messages.getAllValues().stream().map(StationMessage::getContent).toList());
    }

    @Test
    void sendsWelcomeCenterStationCallsignsInGroupsOfSix() {
        WelcomeCenter center = welcomeCenter();
        when(welcomeCenterAccessor.findStationPositions(center.getId()))
                .thenReturn(Flux.just(
                        position("N1ONE"),
                        position("N2TWO"),
                        position("N3THREE"),
                        position("N4FOUR"),
                        position("N5FIVE"),
                        position("N6SIX"),
                        position("N7SEVEN"),
                        position("n1abc"),
                        position("N1ONE"),
                        position("")));

        processor.processCommand(new StationCommand(UUID.randomUUID(), "N1ABC", center, null, "OTHERS", null));

        ArgumentCaptor<StationMessage> messages = ArgumentCaptor.forClass(StationMessage.class);
        verify(stationMessageQueue, times(2)).offer(messages.capture());
        assertEquals(
                List.of("Others (1/2) N1ONE,N2TWO,N3THREE,N4FOUR,N5FIVE,N6SIX", "Others (2/2) N7SEVEN"),
                messages.getAllValues().stream().map(StationMessage::getContent).toList());
    }

    @Test
    void reportsWhenNoOtherStationsAreInWelcomeCenter() {
        WelcomeCenter center = welcomeCenter();
        when(welcomeCenterAccessor.findStationPositions(center.getId())).thenReturn(Flux.empty());

        processor.processCommand(new StationCommand(UUID.randomUUID(), "N1ABC", center, null, "OTHERS", null));

        ArgumentCaptor<StationMessage> message = ArgumentCaptor.forClass(StationMessage.class);
        verify(stationMessageQueue).offer(message.capture());
        assertEquals(
                "No others known to this welcome center", message.getValue().getContent());
    }

    private WelcomeCenter welcomeCenter() {
        WelcomeCenter center = new WelcomeCenter(
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
        org.mockito.Mockito.lenient()
                .when(welcomeCenterAccessor.findOpenById(center.getId()))
                .thenReturn(Mono.just(center));
        return center;
    }

    private StationCommand weatherCommand(WelcomeCenter center) {
        return new StationCommand(UUID.randomUUID(), "N1ABC", center, null, "WEATHER", null);
    }

    private StationPosition position(String callsign) {
        return new StationPosition(UUID.randomUUID(), callsign, "1", "2", LocalDateTime.now());
    }
}
