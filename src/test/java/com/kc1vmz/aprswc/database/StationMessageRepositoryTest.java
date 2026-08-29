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
package com.kc1vmz.aprswc.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class StationMessageRepositoryTest {
    @Autowired
    private StationMessageRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsPacketProcessorId() {
        StationMessage saved = repository.saveAndFlush(new StationMessage(
                null,
                "KC1VMZ-1",
                "KC1VMZ",
                null,
                null,
                "message",
                "packet-processor-1",
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));

        entityManager.clear();
        StationMessage found = repository.findById(saved.getId()).orElseThrow();

        assertEquals("KC1VMZ-1", found.getCallsignTo());
        assertEquals("KC1VMZ", found.getCallsignFrom());
        assertEquals("packet-processor-1", found.getPacketProcessorId());
        assertEquals(MessageType.MESSAGE, found.getMessageType());
    }

    @Test
    void persistsExplicitMessageType() {
        StationMessage saved = repository.saveAndFlush(
                new StationMessage(null, "KC1VMZ-1", "KC1VMZ", null, null, "bulletin", null, MessageType.BULLETIN));

        entityManager.clear();

        assertEquals(
                MessageType.BULLETIN,
                repository.findById(saved.getId()).orElseThrow().getMessageType());
    }

    @Test
    void findsMessagesByDestinationCallsignIgnoringCase() {
        repository.save(new StationMessage(
                null, "N1ABC", "KC1VMZ", null, null, "first", null, com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));
        repository.save(new StationMessage(
                null,
                "N2OTHER",
                "KC1VMZ",
                null,
                null,
                "other",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));

        var found = repository.findAllByCallsignToIgnoreCaseOrderBySentTimeDesc("n1abc");

        assertEquals(1, found.size());
        assertEquals("first", found.get(0).getContent());
    }

    @Test
    void findsMessagesByWelcomeCenter() {
        WelcomeCenter center = entityManager.persistFlushFind(new WelcomeCenter(
                null, "Center", null, "KC1VMZ", null, null, null, null, null, null, null, null, "c", "/", List.of()));
        repository.save(new StationMessage(
                null,
                null,
                "KC1VMZ",
                center,
                null,
                "center message",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));
        repository.save(new StationMessage(
                null,
                "N1ABC",
                "KC1VMZ",
                null,
                null,
                "station message",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));

        var found = repository.findAllByWelcomeCenterIdOrderBySentTimeDesc(center.getId());

        assertEquals(1, found.size());
        assertEquals("center message", found.get(0).getContent());
    }
}
