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

import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.database.StationPacketRepository;
import com.kc1vmz.aprswc.database.StationPositionRepository;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PacketRetentionProcessorTest {
    @Test
    void usesCurrentSettingOnEveryRunAndRecoversAfterFailure() {
        var settings = mock(ApplicationSettingsRepository.class);
        var packets = mock(StationPacketRepository.class);
        var positions = mock(StationPositionRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-09-20T12:34:56Z"), ZoneOffset.UTC);
        var processor = new PacketRetentionProcessor(settings, packets, positions, clock);
        var config = new ApplicationSettings(null, null);
        config.setPacketRetentionDays(7);
        when(settings.findAll()).thenReturn(List.of(config));
        doThrow(new IllegalStateException("Test database failure"))
                .doReturn(1)
                .when(packets)
                .deleteReceivedBefore(LocalDateTime.parse("2026-09-13T12:34:56"));
        processor.deleteExpiredPackets();
        processor.deleteExpiredPackets();
        verify(packets, times(2)).deleteReceivedBefore(LocalDateTime.parse("2026-09-13T12:34:56"));
        verify(positions).deleteCreatedBefore(LocalDateTime.parse("2026-09-13T12:34:56"));
        config.setPacketRetentionDays(1);
        processor.deleteExpiredPackets();
        verify(packets).deleteReceivedBefore(LocalDateTime.parse("2026-09-19T12:34:56"));
        processor.stop();
    }

    @Test
    void defaultsWithoutSettingsAndSkipsInvalidRetention() {
        var settings = mock(ApplicationSettingsRepository.class);
        var packets = mock(StationPacketRepository.class);
        var positions = mock(StationPositionRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-09-20T12:34:56Z"), ZoneOffset.UTC);
        var processor = new PacketRetentionProcessor(settings, packets, positions, clock);
        when(settings.findAll()).thenReturn(List.of());
        processor.deleteExpiredPackets();
        verify(packets)
                .deleteReceivedBefore(
                        LocalDateTime.now(clock).minusDays(ApplicationSettings.DEFAULT_PACKET_RETENTION_DAYS));
        verify(positions).deleteCreatedBefore(LocalDateTime.now(clock).minusDays(1));
        clearInvocations(packets, positions);
        var config = new ApplicationSettings(null, null);
        when(settings.findAll()).thenReturn(List.of(config));
        for (Integer invalid : new Integer[] {0, -1, null}) {
            config.setPacketRetentionDays(invalid);
            processor.deleteExpiredPackets();
        }
        verifyNoInteractions(packets, positions);
        processor.stop();
    }
}
