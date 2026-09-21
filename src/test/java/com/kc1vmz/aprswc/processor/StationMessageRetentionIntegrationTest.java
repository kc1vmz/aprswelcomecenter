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

import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
class StationMessageRetentionIntegrationTest {
    @Autowired
    StationRepository stations;

    @Autowired
    StationPositionRepository positions;

    @Autowired
    StationPacketRepository packets;

    @Autowired
    StationMessageRepository messages;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    TestEntityManager entities;

    @Test
    void expiresInactiveStationsAndAgedMessagesWhileRespectingIndependentCutoffs() {
        var now = LocalDateTime.of(2026, 9, 20, 12, 34, 56);
        var old = new Station(null, "N1OLD", null, List.of());
        old.setLastActivityTime(now.minusDays(11));
        stations.saveAndFlush(old);
        var active = new Station(null, "N1ACTIVE", null, List.of());
        active.setLastActivityTime(now.minusDays(10));
        stations.saveAndFlush(active);
        var position = new StationPosition(null, "N1OLD", "1", "2", now.minusDays(11));
        position.setStation(old);
        positions.saveAndFlush(position);
        var packet =
                packets.saveAndFlush(new StationPacket(null, "source", "N1OLD", now.minusDays(11), "packet", null));
        var expired = messages.saveAndFlush(new StationMessage(
                null, "N1ACTIVE", "N2TEST", null, now.minusDays(11), "expired", null, MessageType.MESSAGE));
        var keep = messages.saveAndFlush(new StationMessage(
                null, "N1OLD", "N2TEST", null, now.minusDays(10), "keep", null, MessageType.MESSAGE));
        var unsent = messages.saveAndFlush(
                new StationMessage(null, "N1ACTIVE", "N2TEST", null, null, "unsent", null, MessageType.MESSAGE));
        entities.getEntityManager()
                .createNativeQuery("update station_messages set created_time=:time where id=:id")
                .setParameter("time", now.minusDays(11))
                .setParameter("id", unsent.getId())
                .executeUpdate();
        entities.clear();
        var settings = mock(ApplicationSettingsRepository.class);
        when(settings.findAll()).thenReturn(List.of());
        var processor = new StationMessageRetentionProcessor(
                settings,
                stations,
                positions,
                messages,
                new TransactionTemplate(transactions),
                Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        processor.cleanup();
        assertThat(stations.existsById(old.getId())).isFalse();
        assertThat(stations.existsById(active.getId())).isTrue();
        assertThat(positions.existsById(position.getId())).isFalse();
        assertThat(packets.existsById(packet.getId())).isTrue();
        assertThat(messages.existsById(expired.getId())).isFalse();
        assertThat(messages.existsById(unsent.getId())).isFalse();
        assertThat(messages.existsById(keep.getId())).isTrue();
        processor.stop();
    }

    @Test
    void recordedActivitySurvivesPacketCleanupAndCannotMoveBackwards() {
        var now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var station = new Station(null, "N1HEARD", null, List.of());
        station.setLastActivityTime(now.minusDays(20));
        stations.saveAndFlush(station);
        stations.recordActivity("n1heard", now);
        stations.recordActivity("N1HEARD", now.minusDays(15));
        packets.deleteReceivedBefore(now.plusDays(1));
        entities.clear();
        assertThat(stations.findById(station.getId()).orElseThrow().getLastActivityTime())
                .isEqualTo(now);
        assertThat(stations.findExpiredIds(now.minusDays(10))).doesNotContain(station.getId());
    }
}
