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

import static org.junit.jupiter.api.Assertions.*;

import com.kc1vmz.aprswc.object.RegionVertex;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolygonGeometryTest {
    private List<RegionVertex> polygon(double... coordinates) {
        List<RegionVertex> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2)
            points.add(new RegionVertex(coordinates[i], coordinates[i + 1]));
        return points;
    }

    @Test
    void concaveBoundaryAndOrientation() {
        var points = polygon(0, 0, 0, 4, 4, 4, 2, 2, 4, 0);
        PolygonGeometry.validate(points);
        assertTrue(PolygonGeometry.covers(points, 1, 2));
        assertFalse(PolygonGeometry.covers(points, 3, 2));
        assertTrue(PolygonGeometry.covers(points, 0, 2));
        assertTrue(PolygonGeometry.covers(points, 2, 2));
        assertTrue(PolygonGeometry.covers(points, 2, 0));
        assertFalse(PolygonGeometry.covers(points, -0.01, 2));
        Collections.reverse(points);
        assertTrue(PolygonGeometry.covers(points, 1, 2));
        assertFalse(PolygonGeometry.covers(points, 3, 2));
    }

    @Test
    void rejectsInvalidBoundaries() {
        for (var points : List.of(
                polygon(0, 0, 2, 2, 0, 2, 2, 0),
                polygon(0, 0, 0, 1, 0, 2),
                polygon(0, 0, 0, 2, 2, 2, 0, 0),
                polygon(0, 0, 0, 4, 0, 2, 2, 2),
                polygon(0, 0, 0, 4, 4, 4, 0, 2, 4, 0),
                polygon(0, 179, 0, -179, 1, 179),
                polygon(86, 0, 84, 1, 84, 0),
                polygon(Double.NaN, 0, 0, 1, 1, 0),
                polygon(0, 0, 0, 1))) {
            assertThrows(IllegalArgumentException.class, () -> PolygonGeometry.validate(points));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> PolygonGeometry.validate(Collections.nCopies(1001, new RegionVertex(0.0, 0.0))));
    }

    @Test
    void acceptsOneThousandVerticesAndRejectsOneThousandAndOne() {
        List<RegionVertex> points = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            double angle = 2 * Math.PI * i / 1000;
            points.add(new RegionVertex(42 + Math.sin(angle), -71 + Math.cos(angle)));
        }
        assertDoesNotThrow(() -> PolygonGeometry.validate(points));
        points.add(new RegionVertex(42.0, -71.0));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PolygonGeometry.validate(points))
                .getMessage()
                .contains("1000"));
    }

    @Test
    void usesMapProjectionRatherThanLatitudeLinearEdges() {
        var triangle = polygon(0, 0, 60, 60, 0, 60);
        PolygonGeometry.validate(triangle);
        // At longitude 30 the Mercator edge is about latitude 35.26, not 30.
        assertTrue(PolygonGeometry.covers(triangle, 33, 30));
        assertFalse(PolygonGeometry.covers(triangle, 37, 30));
        assertFalse(PolygonGeometry.covers(triangle, 90, 30));
        assertFalse(PolygonGeometry.covers(triangle, Double.NaN, 30));
    }

    @Test
    void acceptsStraightContinuationWithoutOverlappingEdges() {
        assertDoesNotThrow(() -> PolygonGeometry.validate(polygon(0, 0, 0, 1, 0, 2, 2, 2, 2, 0)));
    }
}
