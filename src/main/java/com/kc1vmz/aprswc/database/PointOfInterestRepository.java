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

import com.kc1vmz.aprswc.object.PointOfInterest;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PointOfInterestRepository extends JpaRepository<PointOfInterest, UUID> {
    @EntityGraph(attributePaths = {"welcomeCenter", "welcomeCenter.communicationInstanceIds"})
    List<PointOfInterest> findByWelcomeCenterIdOrderByName(UUID centerId);

    @EntityGraph(attributePaths = {"welcomeCenter", "welcomeCenter.communicationInstanceIds"})
    Optional<PointOfInterest> findByName(String name);

    @Override
    @EntityGraph(attributePaths = {"welcomeCenter", "welcomeCenter.communicationInstanceIds"})
    Optional<PointOfInterest> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"welcomeCenter", "welcomeCenter.communicationInstanceIds"})
    List<PointOfInterest> findAll();
}
