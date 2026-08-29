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

import com.kc1vmz.aprswc.object.StationPosition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StationPositionRepository extends JpaRepository<StationPosition, UUID> {
    @Query(
            value =
                    """
                    select latest.*
                    from (
                        select position.*,
                               row_number() over (
                                   partition by position.callsign
                                   order by position.created_time desc nulls last, position.id desc
                               ) as position_rank
                        from station_positions position
                    ) latest
                    where latest.position_rank = 1
                    order by latest.callsign
                    """,
            nativeQuery = true)
    List<StationPosition> findLatestByCallsign();

    @Modifying
    @Transactional
    @Query("delete from StationPosition position where position.station.id = :stationId")
    int deleteAllByStationId(@Param("stationId") UUID stationId);

    @Query("select position from StationPosition position "
            + "where position.station.id = :stationId order by position.createdTime desc")
    List<StationPosition> findAllByStationId(@Param("stationId") UUID stationId);

    @Query("select position from StationPosition position "
            + "where position.station.id = :stationId order by position.createdTime desc")
    List<StationPosition> findRecentByStationId(@Param("stationId") UUID stationId, Pageable pageable);
}
