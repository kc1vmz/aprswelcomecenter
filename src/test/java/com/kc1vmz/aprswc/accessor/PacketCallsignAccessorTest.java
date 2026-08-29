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
package com.kc1vmz.aprswc.accessor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kc1vmz.aprswc.database.IgnoreStationRepository;
import com.kc1vmz.aprswc.database.StationPacketRepository;
import com.kc1vmz.aprswc.database.StationPositionRepository;
import com.kc1vmz.aprswc.database.StationRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.IgnoreStation;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PacketCallsignAccessorTest {
    @Mock
    private WelcomeCenterRepository welcomeCenterRepository;

    @Mock
    private IgnoreStationRepository ignoreStationRepository;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private StationPositionRepository stationPositionRepository;

    @Mock
    private StationPacketRepository stationPacketRepository;

    @Mock
    private StationPacketQueue stationPacketQueue;

    @InjectMocks
    private StationPacketAccessor stationPacketAccessor;

    @InjectMocks
    private IgnoreStationAccessor ignoreStationAccessor;

    @InjectMocks
    private StationAccessor stationAccessor;

    @InjectMocks
    private WelcomeCenterAccessor welcomeCenterAccessor;

    @Test
    void findsWelcomeCenterCallsigns() {
        WelcomeCenter center = new WelcomeCenter(
                UUID.randomUUID(),
                "Center",
                "Description",
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
        when(welcomeCenterRepository.findAll()).thenReturn(List.of(center));

        assertTrue(stationPacketAccessor.isWelcomeCenterCallsign("kc1vmz"));
        assertFalse(stationPacketAccessor.isWelcomeCenterCallsign("N1ABC"));
        assertFalse(stationPacketAccessor.isWelcomeCenterCallsign(null));
    }

    @Test
    void findsWelcomeCenterByCallsign() {
        WelcomeCenter center = new WelcomeCenter(
                UUID.randomUUID(),
                "Center",
                "Description",
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
        when(welcomeCenterRepository.findByCallsignIgnoreCase("kc1vmz")).thenReturn(Optional.of(center));
        when(welcomeCenterRepository.findByCallsignIgnoreCase("MISSING")).thenReturn(Optional.empty());

        StepVerifier.create(welcomeCenterAccessor.findByCallsign("kc1vmz"))
                .expectNext(center)
                .verifyComplete();
        StepVerifier.create(welcomeCenterAccessor.findByCallsign("MISSING"))
                .expectErrorMatches(error -> error instanceof org.springframework.web.server.ResponseStatusException)
                .verify();
        StepVerifier.create(welcomeCenterAccessor.findByCallsign(null))
                .expectErrorMatches(error -> error instanceof org.springframework.web.server.ResponseStatusException)
                .verify();
    }

    @Test
    void findsIgnoredStationCallsigns() {
        when(ignoreStationRepository.findAll()).thenReturn(List.of(new IgnoreStation(UUID.randomUUID(), "N1ABC")));

        assertTrue(ignoreStationAccessor.isIgnoreStation("n1abc"));
        assertFalse(ignoreStationAccessor.isIgnoreStation("KC1VMZ"));
        assertFalse(ignoreStationAccessor.isIgnoreStation(null));
    }

    @Test
    void findsStationByCallsign() {
        Station station = new Station(UUID.randomUUID(), "N1ABC", StationState.STATIONARY, List.of());
        when(stationRepository.findByCallsignIgnoreCase("n1abc")).thenReturn(Optional.of(station));
        when(stationRepository.findByCallsignIgnoreCase("MISSING")).thenReturn(Optional.empty());

        StepVerifier.create(stationAccessor.findByCallsign("n1abc"))
                .expectNext(station)
                .verifyComplete();
        StepVerifier.create(stationAccessor.findByCallsign("MISSING"))
                .expectErrorMatches(error -> error instanceof org.springframework.web.server.ResponseStatusException)
                .verify();
    }

    @Test
    void savesPacketWithoutRequeueingIt() {
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "location", null);
        when(stationPacketRepository.save(packet)).thenReturn(packet);

        StepVerifier.create(stationPacketAccessor.save(packet))
                .expectNext(packet)
                .verifyComplete();

        verify(stationPacketRepository).save(packet);
        verify(stationPacketQueue, never()).offer(packet);
    }
}
