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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.accessor.PointOfInterestService;
import com.kc1vmz.aprswc.accessor.PointOfInterestService.*;
import com.kc1vmz.aprswc.object.ObjectBeacon;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PointOfInterestBeaconProcessorTest {
    @Test
    void committedChangesQueueCorrectReportsAndEvaluatePermissionAtTransmissionTime() {
        var service = mock(PointOfInterestService.class);
        var queue = mock(ObjectBeaconQueue.class);
        var processor = new PointOfInterestBeaconProcessor(service, queue);
        var up = new Snapshot(
                UUID.randomUUID(), 1, "LANDMARK9", "Test", "4336.50N", "07258.30W", "c", "/", "N1TEST", true, null);
        var down = new Snapshot(
                up.id(),
                2,
                up.name(),
                up.description(),
                up.latitude(),
                up.longitude(),
                "c",
                "/",
                "N1TEST",
                false,
                null);
        processor.afterCommit(new Changed(null, down));
        verifyNoInteractions(queue);
        processor.afterCommit(new Changed(null, up));
        var capture = ArgumentCaptor.forClass(ObjectBeacon.class);
        verify(queue).offer(capture.capture());
        var beacon = capture.getValue();
        assertThat(beacon.isActive()).isTrue();
        assertThat(beacon.getCallsignFrom()).isEqualTo("N1TEST");
        when(service.canSend(up, true)).thenReturn(true, false);
        assertThat(beacon.isTransmissionPermitted()).isTrue();
        assertThat(beacon.isTransmissionPermitted()).isFalse();
        clearInvocations(queue);
        processor.afterCommit(new Changed(up, down));
        verify(queue).offer(capture.capture());
        assertThat(capture.getValue().isActive()).isFalse();
        clearInvocations(queue);
        processor.afterCommit(new Changed(up, null));
        verify(queue).offer(capture.capture());
        assertThat(capture.getValue().getObjectName()).isEqualTo("LANDMARK9");
        assertThat(capture.getValue().isActive()).isFalse();
        processor.stop();
    }
}
