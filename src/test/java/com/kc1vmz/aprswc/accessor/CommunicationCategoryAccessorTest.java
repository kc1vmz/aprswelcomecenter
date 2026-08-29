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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kc1vmz.aprswc.database.CommunicationCategoryRepository;
import com.kc1vmz.aprswc.object.CommunicationCategory;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CommunicationCategoryAccessorTest {
    @Test
    void createIfAbsentReturnsCategoryWithExistingName() {
        UUID configuredId = UUID.randomUUID();
        CommunicationCategory configured = new CommunicationCategory(configuredId, "Welcome", "Default", false, true);
        CommunicationCategory existing =
                new CommunicationCategory(UUID.randomUUID(), "Welcome", "Existing", false, false);
        CommunicationCategoryRepository repository = mock(CommunicationCategoryRepository.class);
        when(repository.findById(configuredId)).thenReturn(Optional.empty());
        when(repository.findByName("Welcome")).thenReturn(Optional.of(existing));
        CommunicationCategoryAccessor accessor = new CommunicationCategoryAccessor();
        ReflectionTestUtils.setField(accessor, "repository", repository);

        CommunicationCategory result = accessor.createIfAbsent(configured).block();

        assertSame(existing, result);
        verify(repository, never()).save(configured);
    }
}
