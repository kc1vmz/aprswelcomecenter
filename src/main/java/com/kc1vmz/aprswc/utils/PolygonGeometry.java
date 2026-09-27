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

import com.kc1vmz.aprswc.object.RegionVertex;
import java.util.List;

/** Simple polygons in Leaflet's Web Mercator plane. No date-line wrapping. */
public final class PolygonGeometry {
    public static final int MAX_VERTICES = 1000;
    public static final double MAX_LATITUDE = 85.0511287798066;
    private static final double EPS = 0.000001; // projected metres; numerical tolerance, not GPS buffering

    private PolygonGeometry() {}

    private record Point(double x, double y) {}

    private static Point project(double lat, double lon) {
        return new Point(
                6378137 * Math.toRadians(lon), 6378137 * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2)));
    }

    private static Point project(RegionVertex v) {
        return project(v.getLatitude(), v.getLongitude());
    }

    private static double cross(Point a, Point b, Point p) {
        return (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x);
    }

    private static int side(Point a, Point b, Point p) {
        double c = cross(a, b, p), tolerance = EPS * Math.hypot(b.x - a.x, b.y - a.y);
        return Math.abs(c) <= tolerance ? 0 : c > 0 ? 1 : -1;
    }

    private static boolean on(Point a, Point b, Point p) {
        return side(a, b, p) == 0
                && p.x >= Math.min(a.x, b.x) - EPS
                && p.x <= Math.max(a.x, b.x) + EPS
                && p.y >= Math.min(a.y, b.y) - EPS
                && p.y <= Math.max(a.y, b.y) + EPS;
    }

    private static boolean intersects(Point a, Point b, Point c, Point d) {
        return on(a, b, c)
                || on(a, b, d)
                || on(c, d, a)
                || on(c, d, b)
                || (side(a, b, c) * side(a, b, d) < 0 && side(c, d, a) * side(c, d, b) < 0);
    }

    public static void validate(List<RegionVertex> vertices) {
        if (vertices == null || vertices.size() < 3 || vertices.size() > MAX_VERTICES)
            throw new IllegalArgumentException("Polygon requires 3 to 1000 vertices.");
        for (RegionVertex v : vertices) {
            if (v == null
                    || v.getLatitude() == null
                    || v.getLongitude() == null
                    || !Double.isFinite(v.getLatitude())
                    || !Double.isFinite(v.getLongitude())
                    || Math.abs(v.getLatitude()) > MAX_LATITUDE
                    || Math.abs(v.getLongitude()) > 180)
                throw new IllegalArgumentException("Polygon coordinates exceed supported map limits.");
        }
        double min =
                vertices.stream().mapToDouble(RegionVertex::getLongitude).min().orElseThrow();
        double max =
                vertices.stream().mapToDouble(RegionVertex::getLongitude).max().orElseThrow();
        if (max - min >= 180)
            throw new IllegalArgumentException(
                    "Polygon must span less than 180 degrees and cannot cross the date line.");
        Point[] points = vertices.stream().map(PolygonGeometry::project).toArray(Point[]::new);
        int n = points.length;
        double area = 0;
        for (int i = 0; i < n; i++) {
            Point a = points[i], b = points[(i + 1) % n], c = points[(i + 2) % n];
            area += cross(points[0], a, b);
            if (on(a, b, c) || on(b, c, a)) throw new IllegalArgumentException("Polygon edges must not overlap.");
            for (int j = i + 1; j < n; j++) {
                if (Math.hypot(a.x - points[j].x, a.y - points[j].y) <= EPS)
                    throw new IllegalArgumentException("Polygon vertices must be distinct.");
                if (j == i + 1 || (i == 0 && j == n - 1)) continue;
                if (intersects(a, b, points[j], points[(j + 1) % n]))
                    throw new IllegalArgumentException("Polygon edges must not cross or touch.");
            }
        }
        if (Math.abs(area) <= EPS * EPS) throw new IllegalArgumentException("Polygon must have nonzero area.");
    }

    public static boolean covers(List<RegionVertex> vertices, double latitude, double longitude) {
        if (vertices == null
                || vertices.size() < 3
                || !Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || Math.abs(latitude) > MAX_LATITUDE
                || Math.abs(longitude) > 180) return false;
        Point p = project(latitude, longitude);
        Point[] points = vertices.stream().map(PolygonGeometry::project).toArray(Point[]::new);
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (Point q : points) {
            minX = Math.min(minX, q.x);
            maxX = Math.max(maxX, q.x);
            minY = Math.min(minY, q.y);
            maxY = Math.max(maxY, q.y);
        }
        if (p.x < minX - EPS || p.x > maxX + EPS || p.y < minY - EPS || p.y > maxY + EPS) return false;
        boolean inside = false;
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            Point a = points[j], b = points[i];
            if (on(a, b, p)) return true;
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside;
        }
        return inside;
    }
}
