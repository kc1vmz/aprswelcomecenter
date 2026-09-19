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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.enumeration.WelcomeCenterStatus;
import com.kc1vmz.aprswc.object.*;
import com.kc1vmz.aprswc.processor.ObjectBeaconQueue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class WelcomeCenterLifecycleTest {
    private WelcomeCenterSnapshot snapshot(String callsign, WelcomeCenterStatus status) {
        return new WelcomeCenterSnapshot(
                UUID.randomUUID(), "Name", callsign, "KC1VMZ", "07106.00W", "4218.00N", "c", "/", status);
    }

    @Test
    void renameOnlyBeaconsWhenStayingOpenAndTransitionsDispatchHooks() {
        WelcomeCenterLifecycle lifecycle = spy(new WelcomeCenterLifecycle());
        ObjectBeaconQueue queue = mock(ObjectBeaconQueue.class);
        ReflectionTestUtils.setField(lifecycle, "objectBeaconQueue", queue);
        doNothing().when(lifecycle).onWelcomeCenterClosed(any(), any());
        doNothing().when(lifecycle).onWelcomeCenterOpened(any(), any());
        lifecycle.afterCommit(new WelcomeCenterChanged(
                snapshot("OLD", WelcomeCenterStatus.OPEN), snapshot("NEW", WelcomeCenterStatus.OPEN)));
        ArgumentCaptor<ObjectBeacon> packets = ArgumentCaptor.forClass(ObjectBeacon.class);
        verify(queue, times(2)).offer(packets.capture());
        assertThat(packets.getAllValues()).extracting(ObjectBeacon::isActive).containsExactly(false, true);
        assertThat(packets.getAllValues())
                .extracting(ObjectBeacon::getObjectName)
                .containsExactly("OLD", "NEW");
        clearInvocations(queue);
        lifecycle.afterCommit(new WelcomeCenterChanged(
                snapshot("OLD", WelcomeCenterStatus.CLOSED), snapshot("NEW", WelcomeCenterStatus.CLOSED)));
        lifecycle.afterCommit(new WelcomeCenterChanged(
                snapshot("OLD", WelcomeCenterStatus.OPEN), snapshot("NEW", WelcomeCenterStatus.CLOSED)));
        lifecycle.afterCommit(new WelcomeCenterChanged(
                snapshot("OLD", WelcomeCenterStatus.CLOSED), snapshot("NEW", WelcomeCenterStatus.OPEN)));
        verifyNoInteractions(queue);
        verify(lifecycle).onWelcomeCenterClosed(any(), any());
        verify(lifecycle).onWelcomeCenterOpened(any(), any());
    }
}
