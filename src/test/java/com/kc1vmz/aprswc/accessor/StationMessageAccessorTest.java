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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kc1vmz.aprswc.database.StationMessageRepository;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.processor.StationMessageQueue;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StationMessageAccessorTest {
    @Mock
    private StationMessageRepository repository;

    @Mock
    private StationMessageQueue stationMessageQueue;

    @InjectMocks
    private StationMessageAccessor accessor;

    @Test
    void queuesMessageAfterCreatingIt() {
        StationMessage message = new StationMessage(
                null,
                "N1ABC",
                "KC1VMZ",
                null,
                null,
                "message",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        when(repository.save(message)).thenReturn(message);

        StationMessage saved = accessor.create(message).block();

        assertSame(message, saved);
        verify(repository).save(message);
        verify(stationMessageQueue).offer(message);
    }

    @Test
    void queuesMessageAfterReplacingIt() {
        UUID id = UUID.randomUUID();
        StationMessage existing = new StationMessage(
                id, "N1ABC", "KC1VMZ", null, null, "old", null, com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        StationMessage replacement = new StationMessage(
                null, "N1ABC", "KC1VMZ", null, null, "new", null, com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(replacement)).thenReturn(replacement);

        StationMessage saved = accessor.replace(id, replacement).block();

        assertSame(replacement, saved);
        verify(repository).save(replacement);
        verify(stationMessageQueue).offer(replacement);
    }
}
