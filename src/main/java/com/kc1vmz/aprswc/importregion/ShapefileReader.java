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
package com.kc1vmz.aprswc.importregion;

import com.kc1vmz.aprswc.object.RegionVertex;
import com.kc1vmz.aprswc.utils.PolygonGeometry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.referencing.crs.AbstractCRS;
import org.apache.sis.referencing.cs.AxesConvention;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

/** Bounded reader for the SHP/SHX and dBASE III subset used by polygon boundary imports. */
public final class ShapefileReader {
    public static final int MAX_FEATURES = 20000;
    public static final int MAX_PREVIEW_POINTS = 100000;

    private ShapefileReader() {}

    public record Feature(
            int id,
            Map<String, String> attributes,
            int pointOffset,
            int pointCount,
            int[] starts,
            int outerRings,
            int holes,
            int vertices,
            String problem) {}

    public record Layer(
            int id,
            String name,
            String sourceCrs,
            String warning,
            String problem,
            List<String> fields,
            List<Feature> features,
            byte[] shapes,
            MathTransform transform) {}

    public record Boundary(
            int id, Map<String, String> attributes, int vertices, String problem, List<List<RegionVertex>> rings) {}

    private record Field(String name, int length) {}

    private record Attributes(List<String> fields, List<Map<String, String>> rows, String warning) {}

    public static Layer read(int id, String name, Map<String, byte[]> files, long deadline) {
        try {
            byte[] shp = require(files, "shp"), shx = require(files, "shx");
            header(shp);
            header(shx);
            int type = little(shp).getInt(32);
            check(little(shx).getInt(32) == type, "SHP and SHX geometry types disagree.");
            check((shx.length - 100) % 8 == 0, "Invalid SHX index length.");
            int count = (shx.length - 100) / 8;
            check(count <= MAX_FEATURES, "A layer may contain at most 20,000 features.");
            Attributes attributes = attributes(require(files, "dbf"), files.get("cpg"));
            check(attributes.rows.size() == count, "SHP index and DBF record counts disagree.");
            String problem = null, crsName = "Unknown";
            MathTransform transform = null;
            if (!files.containsKey("prj")) problem = "Missing .prj: provide the source coordinate system file.";
            else {
                try {
                    check(files.get("prj").length <= 16384, "Projection definition is too large.");
                    String wkt = new String(files.get("prj"), StandardCharsets.UTF_8)
                            .replace("\uFEFF", "")
                            .trim();
                    CoordinateReferenceSystem source =
                            AbstractCRS.castOrCopy(CRS.fromWKT(wkt)).forConvention(AxesConvention.DISPLAY_ORIENTED);
                    crsName = source.getName().getCode();
                    check(
                            source.getCoordinateSystem().getDimension() == 2,
                            "Only two-dimensional coordinate systems are supported.");
                    transform = CRS.findOperation(source, CommonCRS.WGS84.normalizedGeographic(), null)
                            .getMathTransform();
                } catch (FactoryException | IllegalArgumentException e) {
                    problem = "Unsupported .prj coordinate system. Re-export this layer in WGS84 (longitude/latitude).";
                }
            }
            List<Feature> features = new ArrayList<>();
            ByteBuffer index = ByteBuffer.wrap(shx), data = little(shp);
            int expectedOffset = 100;
            for (int i = 0; i < count; i++) {
                checkTime(deadline);
                long offset = 2L * index.getInt(100 + i * 8), length = 2L * index.getInt(104 + i * 8);
                check(
                        offset == expectedOffset && length >= 4 && offset + 8 + length <= shp.length,
                        "Invalid SHX record offset or length.");
                int start = (int) offset + 8;
                check(
                        ByteBuffer.wrap(shp).getInt((int) offset) == i + 1
                                && 2L * ByteBuffer.wrap(shp).getInt((int) offset + 4) == length,
                        "SHP record does not match its index.");
                expectedOffset = (int) (offset + 8 + length);
                int recordType = data.getInt(start);
                check(recordType == 0 || recordType == type, "Mixed SHP geometry types are invalid.");
                Map<String, String> row = attributes.rows.get(i);
                if (recordType != 5) {
                    if (row != null)
                        features.add(new Feature(
                                i,
                                row,
                                0,
                                0,
                                new int[0],
                                0,
                                0,
                                0,
                                recordType == 0
                                        ? "Empty geometry."
                                        : "Only 2D Polygon shapes can be imported (this layer has shape type "
                                                + recordType + ")."));
                    continue;
                }
                check(length >= 44, "Truncated polygon record.");
                int parts = data.getInt(start + 36), points = data.getInt(start + 40);
                check(
                        parts > 0 && points >= 4 && parts <= points / 4 && 44L + 4L * parts + 16L * points == length,
                        "Invalid polygon part or point counts.");
                int[] starts = new int[parts + 1];
                for (int j = 0; j < parts; j++) starts[j] = data.getInt(start + 44 + j * 4);
                starts[parts] = points;
                check(starts[0] == 0, "The first polygon part must start at point zero.");
                int pointOffset = start + 44 + parts * 4, outer = 0, holes = 0;
                for (int j = 0; j < parts; j++) {
                    int a = starts[j], end = starts[j + 1];
                    check(a >= 0 && end <= points && end - a >= 4, "Invalid polygon part offsets.");
                    double x0 = data.getDouble(pointOffset + a * 16), y0 = data.getDouble(pointOffset + a * 16 + 8);
                    check(
                            x0 == data.getDouble(pointOffset + (end - 1) * 16)
                                    && y0 == data.getDouble(pointOffset + (end - 1) * 16 + 8),
                            "Polygon ring is not closed.");
                    double area = 0;
                    for (int k = a; k < end - 1; k++) {
                        if ((k & 4095) == 0) checkTime(deadline);
                        double x = data.getDouble(pointOffset + k * 16), y = data.getDouble(pointOffset + k * 16 + 8);
                        double nx = data.getDouble(pointOffset + (k + 1) * 16),
                                ny = data.getDouble(pointOffset + (k + 1) * 16 + 8);
                        check(Double.isFinite(x) && Double.isFinite(y), "Nonfinite polygon coordinates.");
                        area += (x - x0) * (ny - y0) - (nx - x0) * (y - y0);
                    }
                    if (area < 0) outer++;
                    else holes++;
                }
                String issue = null;
                if (parts > 1)
                    issue = holes > 0
                            ? "Holes or multiple rings are not supported."
                            : "Multiple polygon components are not supported.";
                if (points - parts > PolygonGeometry.MAX_VERTICES)
                    issue = (issue == null ? "" : issue + " ")
                            + "Boundary has " + (points - parts)
                            + " vertices and exceeds the 1000-vertex limit; simplify it in a GIS tool before importing.";
                if (row != null)
                    features.add(new Feature(i, row, pointOffset, points, starts, outer, holes, points - parts, issue));
            }
            check(expectedOffset == shp.length, "SHP contains records missing from its index.");
            return new Layer(
                    id,
                    name,
                    crsName,
                    attributes.warning,
                    problem,
                    attributes.fields,
                    List.copyOf(features),
                    shp,
                    transform);
        } catch (IllegalArgumentException e) {
            return new Layer(id, name, "Unknown", null, e.getMessage(), List.of(), List.of(), new byte[0], null);
        }
    }

    public static Boundary boundary(Layer layer, Feature feature, long deadline) {
        String issue = layer.problem != null ? layer.problem : feature.problem;
        if (layer.transform == null || feature.starts.length == 0)
            return new Boundary(feature.id, feature.attributes, feature.vertices, issue, List.of());
        if (feature.pointCount > MAX_PREVIEW_POINTS)
            return new Boundary(
                    feature.id,
                    feature.attributes,
                    feature.vertices,
                    "This feature exceeds the 100,000-point preview limit. Simplify it in a GIS tool.",
                    List.of());
        ByteBuffer data = little(layer.shapes);
        List<List<RegionVertex>> rings = new ArrayList<>();
        try {
            for (int r = 0; r < feature.starts.length - 1; r++) {
                List<RegionVertex> ring = new ArrayList<>();
                double[] xy = new double[2];
                for (int i = feature.starts[r]; i < feature.starts[r + 1] - 1; i++) {
                    checkTime(deadline);
                    xy[0] = data.getDouble(feature.pointOffset + i * 16);
                    xy[1] = data.getDouble(feature.pointOffset + i * 16 + 8);
                    layer.transform.transform(xy, 0, xy, 0, 1);
                    check(
                            Double.isFinite(xy[0])
                                    && Double.isFinite(xy[1])
                                    && Math.abs(xy[0]) <= 180
                                    && Math.abs(xy[1]) <= PolygonGeometry.MAX_LATITUDE,
                            "Transformed boundary exceeds supported map coordinates.");
                    ring.add(new RegionVertex(xy[1], xy[0]));
                }
                rings.add(List.copyOf(ring));
            }
            if (issue == null) PolygonGeometry.validate(rings.get(0));
        } catch (TransformException | IllegalArgumentException e) {
            issue = e instanceof TransformException
                    ? "Coordinate transformation failed. Re-export this layer in WGS84."
                    : e.getMessage();
        }
        return new Boundary(feature.id, feature.attributes, feature.vertices, issue, List.copyOf(rings));
    }

    private static Attributes attributes(byte[] dbf, byte[] cpg) {
        check(dbf.length >= 33 && dbf[0] == 3, "Only dBASE III attribute tables are supported.");
        ByteBuffer data = little(dbf);
        int rows = data.getInt(4),
                header = Short.toUnsignedInt(data.getShort(8)),
                record = Short.toUnsignedInt(data.getShort(10));
        check(
                rows >= 0
                        && rows <= MAX_FEATURES
                        && header >= 33
                        && (header - 33) % 32 == 0
                        && record > 0
                        && header + (long) rows * record <= dbf.length,
                "Invalid DBF record counts or lengths.");
        check(dbf[header - 1] == 13 && (header - 33) / 32 <= 256, "Invalid DBF field header.");
        check(cpg == null || cpg.length <= 256, "Encoding definition is too large.");
        String encoding = cpg == null
                ? switch (Byte.toUnsignedInt(dbf[29])) {
                    case 1 -> "IBM437";
                    case 2 -> "IBM850";
                    case 3, 87 -> "windows-1252";
                    default -> "windows-1252";
                }
                : new String(cpg, StandardCharsets.US_ASCII).trim();
        if (encoding.equals("65001")) encoding = "UTF-8";
        else if (encoding.matches("\\d+")) encoding = "cp" + encoding;
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported .cpg encoding; export attributes as UTF-8.");
        }
        check(
                (long) rows * ((header - 33) / 32) <= 500000,
                "Attribute table exceeds 500,000 cells; upload fewer fields or features.");
        List<Field> fields = new ArrayList<>();
        int sum = 1;
        for (int i = 32; i < header - 1; i += 32) {
            int end = i;
            while (end < i + 11 && dbf[end] != 0) end++;
            String name = new String(dbf, i, end - i, StandardCharsets.US_ASCII);
            check(
                    !name.isBlank() && fields.stream().noneMatch(f -> f.name.equals(name)),
                    "Duplicate or blank DBF field names.");
            check(
                    "CNFDL".indexOf(dbf[i + 11]) >= 0,
                    "Unsupported DBF field type; export ordinary text/numeric attributes.");
            int length = Byte.toUnsignedInt(dbf[i + 16]);
            check(length > 0, "Empty DBF field.");
            fields.add(new Field(name, length));
            sum += length;
        }
        check(sum == record, "DBF fields do not match record length.");
        List<Map<String, String>> values = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            int at = header + row * record;
            if (dbf[at] == '*') {
                values.add(null);
                continue;
            }
            check(dbf[at] == ' ', "Invalid DBF deletion marker.");
            at++;
            Map<String, String> value = new LinkedHashMap<>();
            for (Field field : fields) {
                value.put(field.name, new String(dbf, at, field.length, charset).trim());
                at += field.length;
            }
            values.add(Collections.unmodifiableMap(value));
        }
        return new Attributes(
                fields.stream().map(Field::name).toList(),
                values,
                cpg == null ? "No .cpg file; attribute encoding: " + charset.name() + "." : null);
    }

    private static byte[] require(Map<String, byte[]> files, String extension) {
        byte[] data = files.get(extension);
        check(data != null, "Missing companion ." + extension + " file.");
        return data;
    }

    private static ByteBuffer little(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void header(byte[] bytes) {
        check(
                bytes.length >= 100
                        && ByteBuffer.wrap(bytes).getInt(0) == 9994
                        && little(bytes).getInt(28) == 1000
                        && 2L * ByteBuffer.wrap(bytes).getInt(24) == bytes.length,
                "Invalid shapefile header or length.");
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    static void checkTime(long deadline) {
        check(
                !Thread.currentThread().isInterrupted() && System.nanoTime() < deadline,
                "Import processing exceeded 30 seconds. Upload a smaller collection.");
    }
}
