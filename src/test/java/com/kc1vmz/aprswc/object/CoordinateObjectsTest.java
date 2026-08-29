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
package com.kc1vmz.aprswc.object;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoordinateObjectsTest {
    @Test
    void coordinatePairProvidesAccessors() {
        CoordinatePair pair = new CoordinatePair("07106.00W", "4218.00N");

        pair.setLongitude("07107.00W");
        pair.setLatitude("4219.00N");

        assertAll(
                () -> assertEquals("07107.00W", pair.getLongitude()),
                () -> assertEquals("4219.00N", pair.getLatitude()));
    }

    @Test
    void decimalCoordinateProvidesPositiveAndNegativeResults() {
        DecimalCoordinate valid =
                new DecimalCoordinate(new BigDecimal("-71.1000"), new BigDecimal("42.3000"), true, null);
        DecimalCoordinate invalid = new DecimalCoordinate(null, null, false, "Invalid coordinate");

        valid.setLongitude(new BigDecimal("-71.2000"));
        valid.setLatitude(new BigDecimal("42.4000"));
        valid.setValid(true);
        valid.setError(null);

        assertAll(
                () -> assertEquals(new BigDecimal("-71.2000"), valid.getLongitude()),
                () -> assertEquals(new BigDecimal("42.4000"), valid.getLatitude()),
                () -> assertTrue(valid.isValid()),
                () -> assertNull(valid.getError()),
                () -> assertNull(invalid.getLongitude()),
                () -> assertNull(invalid.getLatitude()),
                () -> assertFalse(invalid.isValid()),
                () -> assertEquals("Invalid coordinate", invalid.getError()));
    }

    @Test
    void decimalCoordinatePairProvidesAccessors() {
        DecimalCoordinatePair pair = new DecimalCoordinatePair(new BigDecimal("-71.1"), new BigDecimal("42.3"));

        pair.setLongitude(new BigDecimal("-72.2"));
        pair.setLatitude(new BigDecimal("43.4"));

        assertAll(
                () -> assertEquals(new BigDecimal("-72.2"), pair.getLongitude()),
                () -> assertEquals(new BigDecimal("43.4"), pair.getLatitude()));
    }
}
