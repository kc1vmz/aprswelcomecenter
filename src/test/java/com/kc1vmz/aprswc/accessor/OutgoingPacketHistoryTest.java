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

import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.database.StationPacketRepository;
import com.kc1vmz.aprswc.database.StationRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutgoingPacketHistoryTest {
    @Test
    void savesOnlyHistoryAndSuppressesNoArchivePayloads() {
        var repository = mock(StationPacketRepository.class);
        var stations = mock(StationRepository.class);
        var queue = mock(StationPacketQueue.class);
        var centers = mock(WelcomeCenterRepository.class);
        var settings = mock(ApplicationSettingsRepository.class);
        var configuration = new ApplicationSettings(null, null, ApplicationSettings.DEFAULT_TINY_TOPICS_SERVER_URL);
        configuration.setPacketLoggingEnabled(true);
        when(settings.findAll()).thenReturn(List.of(configuration));
        var accessor = new StationPacketAccessor(repository, stations, queue, centers, settings);
        var packet = new StationPacket(null, "route", "N1TEST", null, "N1TEST>APRS::N2TEST   :Hello{00001", null, "TX");
        accessor.recordTransmitted(packet);
        verify(repository).save(packet);
        accessor.recordTransmitted(
                new StationPacket(null, "route", "N1TEST", null, "N1TEST>APRS::N2TEST   :private !x!", null, "TX"));
        configuration.setPacketLoggingEnabled(false);
        accessor.recordTransmitted(packet);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(stations, queue, centers);
    }
}
