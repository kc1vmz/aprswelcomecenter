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

import static org.assertj.core.api.Assertions.*;

import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.StationPosition;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class PacketRetentionRepositoryTest {
    @Autowired
    StationPacketRepository packets;

    @Autowired
    StationPositionRepository positions;

    @Autowired
    StationRepository stations;

    @Autowired
    StationMessageRepository messages;

    @Test
    void expiresPositionsButKeepsStationsAndMessages() {
        var cutoff = LocalDateTime.of(2026, 9, 20, 12, 34, 56);
        var station = stations.saveAndFlush(new Station(null, "N1KEEP", null, List.of()));
        var old = new StationPosition(null, station.getCallsign(), "1", "2", cutoff.minusSeconds(1));
        var boundary = new StationPosition(null, station.getCallsign(), "1", "2", cutoff);
        var recent = new StationPosition(null, station.getCallsign(), "1", "2", cutoff.plusSeconds(1));
        for (var position : List.of(old, boundary, recent)) {
            position.setStation(station);
            positions.saveAndFlush(position);
        }
        var message = messages.saveAndFlush(new StationMessage(
                null,
                station.getCallsign(),
                "N2KEEP",
                null,
                cutoff.minusDays(10),
                "Keep this message",
                null,
                MessageType.MESSAGE));
        packets.deleteReceivedBefore(cutoff);
        assertThat(positions.deleteCreatedBefore(cutoff)).isEqualTo(1);
        assertThat(positions.existsById(old.getId())).isFalse();
        assertThat(positions.existsById(boundary.getId())).isTrue();
        assertThat(positions.existsById(recent.getId())).isTrue();
        assertThat(stations.existsById(station.getId())).isTrue();
        assertThat(messages.existsById(message.getId())).isTrue();
    }

    @Test
    void deletesOnlyPacketsStrictlyOlderThanCutoff() {
        var cutoff = LocalDateTime.of(2026, 9, 20, 12, 34, 56);
        var old = packets.saveAndFlush(new StationPacket(null, "source", "N1OLD", cutoff.minusSeconds(1), "old", null));
        var boundary = packets.saveAndFlush(new StationPacket(null, "source", "N1EDGE", cutoff, "boundary", null));
        var recent =
                packets.saveAndFlush(new StationPacket(null, "source", "N1NEW", cutoff.plusSeconds(1), "recent", null));
        assertThat(packets.deleteReceivedBefore(cutoff)).isEqualTo(1);
        assertThat(packets.existsById(old.getId())).isFalse();
        assertThat(packets.existsById(boundary.getId())).isTrue();
        assertThat(packets.existsById(recent.getId())).isTrue();
    }
}
