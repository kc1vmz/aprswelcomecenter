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
package com.kc1vmz.aprswc.accessor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kc1vmz.aprswc.object.CoordinatePair;
import com.kc1vmz.aprswc.object.DecimalCoordinate;
import com.kc1vmz.aprswc.object.DecimalCoordinatePair;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoordinateAccessorTest {
    @Test
    void convertsAprsCoordinatesToDecimalCoordinates() {
        CoordinateAccessor accessor = new CoordinateAccessor();

        List<DecimalCoordinate> converted = accessor.convert(List.of(new CoordinatePair("07106.00W", "4218.00N")))
                .block();

        DecimalCoordinate result = converted.get(0);
        assertAll(
                () -> assertEquals(1, converted.size()),
                () -> assertEquals(new BigDecimal("-71.1"), result.getLongitude()),
                () -> assertEquals(new BigDecimal("42.3"), result.getLatitude()),
                () -> assertTrue(result.isValid()));
    }

    @Test
    void marksInvalidAprsCoordinatesAsInvalid() {
        CoordinateAccessor accessor = new CoordinateAccessor();

        DecimalCoordinate result = accessor.convertCoordinate(new CoordinatePair("invalid", "invalid"));

        assertAll(
                () -> assertEquals(BigDecimal.valueOf(1000.0), result.getLongitude()),
                () -> assertEquals(BigDecimal.valueOf(1000.0), result.getLatitude()),
                () -> assertFalse(result.isValid()),
                () -> assertEquals("Bad coordinates", result.getError()));
    }

    @Test
    void convertsAnEmptyRequestToAnEmptyResponse() {
        CoordinateAccessor accessor = new CoordinateAccessor();

        assertEquals(List.of(), accessor.convert(List.of()).block());
    }

    @Test
    void convertsDecimalCoordinatesToAprsCoordinates() {
        CoordinateAccessor accessor = new CoordinateAccessor();

        CoordinatePair result = accessor.convertToAprsCoordinate(
                new DecimalCoordinatePair(new BigDecimal("-71.1"), new BigDecimal("42.3")));

        assertAll(
                () -> assertEquals("07106.00W", result.getLongitude()),
                () -> assertEquals("4218.00N", result.getLatitude()));
    }

    @Test
    void rejectsDecimalCoordinatesOutsideAprsRanges() {
        CoordinateAccessor accessor = new CoordinateAccessor();

        CoordinatePair result = accessor.convertToAprsCoordinate(
                new DecimalCoordinatePair(new BigDecimal("181"), new BigDecimal("91")));

        assertAll(() -> assertNull(result.getLongitude()), () -> assertNull(result.getLatitude()));
    }
}
