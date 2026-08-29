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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.StationCommandType;
import com.kc1vmz.aprswc.object.CommunicationEvent;
import com.kc1vmz.aprswc.object.StationCommand;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPacket;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndependentQueuesTest {
    @Test
    void stationPacketQueueIsFifo() throws InterruptedException {
        StationPacketQueue queue = new StationPacketQueue();
        StationPacket first =
                new StationPacket(UUID.randomUUID(), "processor-1", "A", LocalDateTime.now(), "first", null);
        StationPacket second =
                new StationPacket(UUID.randomUUID(), "processor-2", "B", LocalDateTime.now(), "second", null);
        queue.offer(first);
        queue.offer(second);
        assertEquals(2, queue.size());
        assertSame(first, queue.take());
        assertSame(second, queue.take());
    }

    @Test
    void otherQueuesStoreTheirOwnTypes() throws InterruptedException {
        CommunicationEventQueue eventQueue = new CommunicationEventQueue();
        StationCommandQueue commandQueue = new StationCommandQueue();
        StationMessageQueue messageQueue = new StationMessageQueue();
        CommunicationEvent event =
                new CommunicationEvent(null, null, CommunicationEventType.ENTER_REGION, null, null, null);
        StationCommand command = new StationCommand(null, "A", null, null, "INFO", StationCommandType.INFO);
        StationMessage message = new StationMessage(
                null, "A", null, null, null, "content", null, com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        eventQueue.offer(event);
        commandQueue.offer(command);
        messageQueue.offer(message);
        assertSame(event, eventQueue.take());
        assertSame(command, commandQueue.take());
        assertSame(message, messageQueue.take());
    }
}
