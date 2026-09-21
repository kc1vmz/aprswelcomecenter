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

import com.kc1vmz.aprswc.object.StationMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StationMessageRepository extends JpaRepository<StationMessage, UUID> {
    @Modifying
    @Transactional
    @Query("delete from StationMessage m where coalesce(m.sentTime, m.createdTime) < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") java.time.LocalDateTime cutoff);

    List<StationMessage> findAllByCallsignToIgnoreCaseOrderBySentTimeDesc(String callsignTo);

    List<StationMessage> findAllByWelcomeCenterIdOrderBySentTimeDesc(UUID welcomeCenterId);

    @Modifying
    @Transactional
    @Query("delete from StationMessage message where upper(message.callsignTo) = upper(:callsignTo)")
    int deleteAllByCallsignToIgnoreCase(@Param("callsignTo") String callsignTo);

    @Modifying
    @Transactional
    @Query("delete from StationMessage message where message.welcomeCenter.id = :welcomeCenterId")
    int deleteAllByWelcomeCenterId(@Param("welcomeCenterId") UUID welcomeCenterId);
}
