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

import static org.assertj.core.api.Assertions.assertThat;

import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPosition;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class StationPositionRepositoryTest {
    @Autowired
    private StationRepository stations;

    @Autowired
    private StationPositionRepository positions;

    @Test
    void findsOnlyTheLatestPositionForEachCallsign() {
        Station firstStation = new Station(null, "N1ABC", StationState.MOVING, List.of());
        firstStation.addPosition(
                new StationPosition(null, "N1ABC", "07106.00W", "4218.00N", LocalDateTime.of(2026, 8, 24, 10, 0)));
        StationPosition latestFirstPosition =
                new StationPosition(null, "N1ABC", "07105.00W", "4219.00N", LocalDateTime.of(2026, 8, 24, 11, 0));
        firstStation.addPosition(latestFirstPosition);

        Station secondStation = new Station(null, "N2ABC", StationState.STATIONARY, List.of());
        StationPosition latestSecondPosition =
                new StationPosition(null, "N2ABC", "07200.00W", "4300.00N", LocalDateTime.of(2026, 8, 24, 9, 0));
        secondStation.addPosition(latestSecondPosition);
        stations.saveAllAndFlush(List.of(firstStation, secondStation));

        List<StationPosition> latestPositions = positions.findLatestByCallsign();

        assertThat(latestPositions)
                .extracting(StationPosition::getId)
                .containsExactly(latestFirstPosition.getId(), latestSecondPosition.getId());
    }
}
