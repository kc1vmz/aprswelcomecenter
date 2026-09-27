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

import static org.assertj.core.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShapefileReaderTest {
    private Map<String, byte[]> shape(double[][]... rings) throws Exception {
        var files = acton();
        int points = 0;
        for (double[][] ring : rings) points += ring.length;
        int length = 44 + 4 * rings.length + 16 * points;
        byte[] shp = new byte[108 + length];
        System.arraycopy(files.get("shp"), 0, shp, 0, 100);
        ByteBuffer.wrap(shp).putInt(24, shp.length / 2).putInt(100, 1).putInt(104, length / 2);
        ByteBuffer data = ByteBuffer.wrap(shp).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(108, 5).putInt(144, rings.length).putInt(148, points);
        int index = 0;
        for (int i = 0; i < rings.length; i++) {
            data.putInt(152 + i * 4, index);
            index += rings[i].length;
        }
        data.position(152 + rings.length * 4);
        for (double[][] ring : rings)
            for (double[] point : ring) data.putDouble(point[0]).putDouble(point[1]);
        files.put("shp", shp);
        ByteBuffer.wrap(files.get("shx")).putInt(104, length / 2);
        return files;
    }

    @Test
    void rejectsMultipartHolesOversizeAndSelfCrossingsWithoutDroppingGeometry() throws Exception {
        double[][] outer = {{200000, 900000}, {200000, 901000}, {201000, 901000}, {201000, 900000}, {200000, 900000}};
        double[][] hole = {{200100, 900100}, {200900, 900100}, {200900, 900900}, {200100, 900900}, {200100, 900100}};
        var layer = ShapefileReader.read(0, "holes", shape(outer, hole), deadline());
        assertThat(layer.features().get(0).holes()).isEqualTo(1);
        var boundary = ShapefileReader.boundary(layer, layer.features().get(0), deadline());
        assertThat(boundary.problem()).contains("Holes");
        assertThat(boundary.rings()).hasSize(2);
        layer = ShapefileReader.read(0, "parts", shape(outer, outer), deadline());
        assertThat(layer.features().get(0).problem()).contains("Multiple polygon");
        double[][] many = new double[1002][2];
        for (int i = 0; i < 1001; i++) {
            double angle = 2 * Math.PI * i / 1001;
            many[i] = new double[] {200000 + 1000 * Math.cos(angle), 900000 + 1000 * Math.sin(angle)};
        }
        many[1001] = many[0];
        layer = ShapefileReader.read(0, "large", shape(many), deadline());
        assertThat(layer.features().get(0).vertices()).isEqualTo(1001);
        assertThat(layer.features().get(0).problem()).contains("1001 vertices", "1000-vertex");
        double[][] crossed = {{200000, 900000}, {201000, 901000}, {200000, 901000}, {201000, 900000}, {200000, 900000}};
        layer = ShapefileReader.read(0, "crossed", shape(crossed), deadline());
        assertThat(ShapefileReader.boundary(layer, layer.features().get(0), deadline())
                        .problem())
                .contains("cross");
    }

    static Map<String, byte[]> acton() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        for (String ext : new String[] {"shp", "shx", "dbf", "prj", "cpg"}) {
            try (var in = ShapefileReaderTest.class.getResourceAsStream("/shapefile/acton/acton." + ext)) {
                files.put(ext, in.readAllBytes());
            }
        }
        return files;
    }

    private long deadline() {
        return System.nanoTime() + 30_000_000_000L;
    }

    @Test
    void actonConvertsProjectedMetersToOrderedWgs84Vertices() throws Exception {
        var layer = ShapefileReader.read(0, "Acton", acton(), deadline());
        assertThat(layer.problem()).isNull();
        assertThat(layer.features()).hasSize(1);
        var feature = layer.features().get(0);
        assertThat(feature.attributes()).containsEntry("TOWN", "ACTON").containsEntry("COUNTY", "MIDDLESEX");
        assertThat(feature.vertices()).isEqualTo(12);
        var boundary = ShapefileReader.boundary(layer, feature, deadline());
        assertThat(boundary.problem()).isNull();
        assertThat(boundary.rings()).hasSize(1);
        var vertices = boundary.rings().get(0);
        assertThat(vertices).hasSize(12);
        assertThat(vertices.get(0).getLongitude()).isCloseTo(-71.38494690485916, within(0.000001));
        assertThat(vertices.get(0).getLatitude()).isCloseTo(42.50446446980484, within(0.000001));
        assertThat(vertices.get(11).getLongitude()).isCloseTo(-71.39156350715194, within(0.000001));
    }

    @Test
    void missingAndUnknownProjectionDoNotGuessCoordinates() throws Exception {
        var files = acton();
        files.remove("prj");
        var missing = ShapefileReader.read(0, "Acton", files, deadline());
        assertThat(missing.problem()).contains("Missing .prj");
        assertThat(ShapefileReader.boundary(missing, missing.features().get(0), deadline())
                        .rings())
                .isEmpty();
        files.put("prj", "not a projection".getBytes(StandardCharsets.UTF_8));
        assertThat(ShapefileReader.read(0, "Acton", files, deadline()).problem())
                .contains("Unsupported .prj");
    }

    @Test
    void rejectsBrokenIndexAndMalformedCountsBeforeAllocating() throws Exception {
        var files = acton();
        ByteBuffer.wrap(files.get("shx")).putInt(100, Integer.MAX_VALUE);
        assertThat(ShapefileReader.read(0, "Acton", files, deadline()).problem())
                .contains("offset");
        files = acton();
        ByteBuffer.wrap(files.get("shp")).order(ByteOrder.LITTLE_ENDIAN).putInt(148, Integer.MAX_VALUE);
        assertThat(ShapefileReader.read(0, "Acton", files, deadline()).problem())
                .contains("counts");
        files = acton();
        files.remove("dbf");
        assertThat(ShapefileReader.read(0, "Acton", files, deadline()).problem())
                .contains(".dbf");
    }

    @Test
    void respectsUtf8AttributesAndDeletedRecords() throws Exception {
        var files = acton();
        byte[] dbf = files.get("dbf");
        int header = Short.toUnsignedInt(
                ByteBuffer.wrap(dbf).order(ByteOrder.LITTLE_ENDIAN).getShort(8));
        byte[] name = "ACTÓN".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(name, 0, dbf, header + 1, name.length);
        assertThat(ShapefileReader.read(0, "Acton", files, deadline())
                        .features()
                        .get(0)
                        .attributes()
                        .get("TOWN"))
                .isEqualTo("ACTÓN");
        dbf[header] = '*';
        assertThat(ShapefileReader.read(0, "Acton", files, deadline()).features())
                .isEmpty();
    }
}
