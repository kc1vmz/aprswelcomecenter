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

import com.kc1vmz.aprswc.object.Station;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationRepository extends JpaRepository<Station, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query(
            "update Station s set s.lastActivityTime = :time where upper(s.callsign) = upper(:callsign) and s.lastActivityTime < :time")
    int recordActivity(
            @org.springframework.data.repository.query.Param("callsign") String callsign,
            @org.springframework.data.repository.query.Param("time") java.time.LocalDateTime time);

    @org.springframework.data.jpa.repository.Query("select s.id from Station s where s.lastActivityTime < :cutoff")
    java.util.List<UUID> findExpiredIds(
            @org.springframework.data.repository.query.Param("cutoff") java.time.LocalDateTime cutoff);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Station s where s.id = :id")
    Optional<Station> findForRetention(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<Station> findByCallsignIgnoreCase(String callsign);
}
