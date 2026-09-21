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

import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class StationMessageRetentionProcessorTest {
    @Test
    void readsIndependentSettingsEachRunAndMessageCleanupSurvivesStationFailure() {
        var settings = mock(ApplicationSettingsRepository.class);
        var stations = mock(StationRepository.class);
        var positions = mock(StationPositionRepository.class);
        var messages = mock(StationMessageRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-09-20T12:34:56Z"), ZoneOffset.UTC);
        var processor = new StationMessageRetentionProcessor(
                settings, stations, positions, messages, mock(TransactionTemplate.class), clock);
        when(settings.findAll()).thenReturn(List.of());
        when(stations.findExpiredIds(any())).thenThrow(new IllegalStateException("Test failure"));
        processor.cleanup();
        verify(stations).findExpiredIds(LocalDateTime.now(clock).minusDays(10));
        verify(messages).deleteExpiredBefore(LocalDateTime.now(clock).minusDays(10));
        var config = new ApplicationSettings(null, null);
        config.setStationRetentionDays(5);
        config.setMessageRetentionDays(20);
        when(settings.findAll()).thenReturn(List.of(config));
        processor.cleanup();
        verify(stations).findExpiredIds(LocalDateTime.now(clock).minusDays(5));
        verify(messages).deleteExpiredBefore(LocalDateTime.now(clock).minusDays(20));
        processor.stop();
    }
}
