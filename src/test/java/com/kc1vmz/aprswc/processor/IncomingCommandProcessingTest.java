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

import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.communication.IncomingCommandService;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.parser.PacketParser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class IncomingCommandProcessingTest {
    @Mock
    WelcomeCenterAccessor centers;

    @Mock
    StationCommandProcessor commands;

    @Mock
    StationCommandQueue commandQueue;

    @Mock
    StationMessageQueue messages;

    @Mock
    IncomingCommandService incoming;

    @InjectMocks
    StationPacketProcessor processor;

    @Test
    void acknowledgesRepeatedRequestButRunsCommandOnceWithoutMutatingRawPacket() {
        var center = new WelcomeCenter(
                UUID.randomUUID(),
                "Center",
                "",
                "K4BO",
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
        when(centers.findOpenByCallsign("K4BO")).thenReturn(Mono.just(center));
        when(commands.isRecognizedCommand("v{18")).thenReturn(true);
        when(incoming.claim(center.getId(), "N2EDX-7", "v{18")).thenReturn(true, false);
        for (String path : List.of("NFL2-2", "N2EDX-10,NFL2-1")) {
            String raw = "N2EDX-7>APK005," + path + "::K4BO     :v{18\r";
            var original = new StationPacket(null, "route", "N2EDX-7", null, raw, null);
            var parsed = new StationPacket(original);
            new PacketParser().parse(parsed);
            ReflectionTestUtils.invokeMethod(processor, "processMessagePacket", parsed, null);
            assertThat(original.getCommand()).isEqualTo(raw);
        }
        var acknowledgements = ArgumentCaptor.forClass(StationMessage.class);
        verify(messages, times(2)).offer(acknowledgements.capture());
        assertThat(acknowledgements.getAllValues()).allSatisfy(ack -> {
            assertThat(ack.getContent()).isEqualTo("ack18");
            assertThat(ack.getMessageIdentifier()).isNull();
        });
        verify(commandQueue, times(1)).offer(any());
    }
}
