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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StationPacketRepository extends JpaRepository<StationPacket, UUID> {
    @Modifying
    @Transactional
    @Query("delete from StationPacket packet where packet.receivedTime < :cutoff")
    int deleteReceivedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query(
            "select p from StationPacket p where lower(p.callsign) = lower(:callsign) and p.direction = 'RX' order by p.receivedTime desc")
    List<StationPacket> findAllByCallsignIgnoreCaseOrderByReceivedTimeDesc(String callsign);

    interface LastHeard {
        String getCallsign();

        LocalDateTime getReceivedTime();
    }

    @Query(
            value = "select callsign, max(received_time) as receivedTime from ("
                    + "select upper(callsign) as callsign, received_time from station_packets where direction='RX' "
                    + "union all select callsign, received_time from station_packet_routes) group by callsign",
            nativeQuery = true)
    List<LastHeard> findLastHeardByCallsign();

    interface HeardRoute extends LastHeard {
        String getPacketProcessorId();
    }

    @Query(
            value =
                    "select max(callsign) as callsign, packet_processor_id as packetProcessorId, max(received_time) as receivedTime "
                            + "from (select upper(callsign) as callsign, packet_processor_id, received_time from station_packets where direction='RX' "
                            + "union all select callsign, packet_processor_id, received_time from station_packet_routes) "
                            + "where callsign=upper(:callsign) group by packet_processor_id order by max(received_time) desc",
            nativeQuery = true)
    List<HeardRoute> findHeardRoutes(@Param("callsign") String callsign);

    @Modifying
    @Transactional
    @Query(
            value =
                    "merge into station_packet_routes t using (values (cast(:callsign as varchar(10)), cast(:route as varchar(255)), cast(:time as timestamp))) "
                            + "s(callsign,route,heard) on t.callsign=s.callsign and t.packet_processor_id=s.route "
                            + "when matched and t.received_time < s.heard then update set received_time=s.heard "
                            + "when not matched then insert(callsign,packet_processor_id,received_time) values(s.callsign,s.route,s.heard)",
            nativeQuery = true)
    void recordRoute(
            @Param("callsign") String callsign, @Param("route") String route, @Param("time") LocalDateTime time);

    @Modifying
    @Transactional
    @Query(value = "delete from station_packet_routes where received_time < :cutoff", nativeQuery = true)
    int deleteRoutesBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Transactional
    @Query("delete from StationPacket packet where lower(packet.callsign) = lower(:callsign)")
    int deleteRawByCallsign(@Param("callsign") String callsign);

    @Modifying
    @Transactional
    @Query(value = "delete from station_packet_routes where callsign=upper(:callsign)", nativeQuery = true)
    int deleteRoutesByCallsign(@Param("callsign") String callsign);

    @Transactional
    default int deleteAllByCallsignIgnoreCase(String callsign) {
        deleteRoutesByCallsign(callsign);
        return deleteRawByCallsign(callsign);
    }
}
