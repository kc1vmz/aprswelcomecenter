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

import com.kc1vmz.aprswc.object.StationPacket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StationPacketRepository extends JpaRepository<StationPacket, UUID> {
    List<StationPacket> findAllByCallsignIgnoreCaseOrderByReceivedTimeDesc(String callsign);

    interface LastHeard {
        String getCallsign();

        java.time.LocalDateTime getReceivedTime();
    }

    @Query("select upper(packet.callsign) as callsign, max(packet.receivedTime) as receivedTime "
            + "from StationPacket packet group by upper(packet.callsign)")
    List<LastHeard> findLastHeardByCallsign();

    @Modifying
    @Transactional
    @Query("delete from StationPacket packet where lower(packet.callsign) = lower(:callsign)")
    int deleteAllByCallsignIgnoreCase(@Param("callsign") String callsign);
}
