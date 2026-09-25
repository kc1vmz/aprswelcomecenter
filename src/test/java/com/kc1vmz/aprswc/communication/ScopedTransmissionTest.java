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
package com.kc1vmz.aprswc.communication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.object.*;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import com.kc1vmz.aprswc.processor.aprs.is.APRSUtilityAccessor;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScopedTransmissionTest {
    @Test
    @SuppressWarnings("unchecked")
    void objectsBulletinsAndRepliesRecheckSelectionAtWriteTime() {
        var id = UUID.randomUUID();
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var center = new WelcomeCenter(
                id,
                "Center",
                null,
                "N1TEST",
                null,
                null,
                null,
                null,
                null,
                null,
                "07258.30W",
                "4336.50N",
                "c",
                "/",
                List.of());
        var centers = mock(WelcomeCenterRepository.class);
        when(centers.findById(id)).thenReturn(Optional.of(center));
        when(centers.findByCallsignIgnoreCase("N1TEST")).thenReturn(Optional.of(center));
        var routing = new CenterCommunicationRouting(centers, mock(CommunicationInstanceRepository.class), null, null);
        var manager = new CommunicationInstanceManager(
                mock(CommunicationInstanceRepository.class), new StationPacketQueue(), mock(APRSUtilityAccessor.class));
        ReflectionTestUtils.setField(manager, "routing", routing);
        Map<UUID, ManagedPacketListener> workers =
                (Map<UUID, ManagedPacketListener>) ReflectionTestUtils.getField(manager, "workers");
        var pendingA = new AtomicReference<BooleanSupplier>();
        var pendingB = new AtomicReference<BooleanSupplier>();
        for (var entry : Map.of(a, pendingA, b, pendingB).entrySet()) {
            var worker = mock(ManagedPacketListener.class);
            when(worker.send(any(), any(BooleanSupplier.class), any())).thenAnswer(invocation -> {
                entry.getValue().set(invocation.getArgument(1));
                return true;
            });
            workers.put(entry.getKey(), worker);
        }
        var object = new ObjectBeacon(
                "LANDMARK", "N1TEST", "07258.30W", "4336.50N", "c", "/", "POI", true, (CommunicationScope.of(center)));
        manager.sendObject(object);
        assertThat(pendingA.get().getAsBoolean()).isTrue();
        assertThat(pendingB.get().getAsBoolean()).isTrue();
        center.setCommunicationMode("SELECTED");
        center.setCommunicationInstanceIds(Set.of(a));
        assertThat(pendingA.get().getAsBoolean()).isTrue();
        assertThat(pendingB.get().getAsBoolean()).isFalse();
        manager.sendBulletin(id, "N1TEST", "BLN1", "News", () -> true);
        assertThat(pendingA.get().getAsBoolean()).isTrue();
        assertThat(pendingB.get().getAsBoolean()).isFalse();
        manager.sendMessage(a.toString(), "N1TEST", "N2TEST", "Reply", () -> {});
        assertThat(pendingA.get().getAsBoolean()).isTrue();
        center.setCommunicationInstanceIds(Set.of());
        assertThat(pendingA.get().getAsBoolean()).isFalse();
        manager.stop();
    }
}
