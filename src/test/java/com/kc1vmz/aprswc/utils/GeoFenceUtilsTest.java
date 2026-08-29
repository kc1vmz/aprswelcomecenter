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
package com.kc1vmz.aprswc.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kc1vmz.aprswc.enumeration.DistanceUnit;
import com.kc1vmz.aprswc.enumeration.RegionType;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

class GeoFenceUtilsTest {
    @Test
    void isSpringBeanWithStubbedCircleResult() {
        StationPosition position =
                new StationPosition(UUID.randomUUID(), "NOCALL-1", "07106.00W", "4218.00N", LocalDateTime.now());

        assertTrue(GeoFenceUtils.class.isAnnotationPresent(Component.class));
        assertFalse(new GeoFenceUtils().isInGeofenceCircle("07100.00W", "4200.00N", 10, DistanceUnit.MILES, position));
    }

    @Test
    void returnsFalseWhileRegionCheckIsStubbed() {
        WelcomeRegion region = new WelcomeRegion(
                UUID.randomUUID(),
                "Region",
                "Description",
                RegionType.CIRCLE,
                null,
                null,
                null,
                null,
                "07100.00W",
                "4200.00N",
                10,
                DistanceUnit.MILES);
        StationPosition position =
                new StationPosition(UUID.randomUUID(), "NOCALL-1", "07106.00W", "4218.00N", LocalDateTime.now());

        assertFalse(new GeoFenceUtils().isInGeoFenceRegion(region, position));
    }

    @Test
    void identifiesPositionInsideRectangle() {
        StationPosition position =
                new StationPosition(UUID.randomUUID(), "NOCALL-1", "07106.00W", "4218.00N", LocalDateTime.now());

        assertTrue(
                new GeoFenceUtils().isInGeoFenceRectangle("07110.00W", "4220.00N", "07100.00W", "4210.00N", position));
    }
}
