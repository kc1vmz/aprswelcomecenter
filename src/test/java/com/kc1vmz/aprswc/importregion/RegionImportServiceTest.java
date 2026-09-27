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
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RegionImportServiceTest {
    static byte[] zip(Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    @Test
    void folderGroupsCompanionsByRelativePathAndPaginates() throws Exception {
        var service = new RegionImportService();
        try (var upload = service.begin()) {
            for (var file : ShapefileReaderTest.acton().entrySet()) {
                upload.append("folder/one/ACTON." + file.getKey(), file.getValue(), true);
                upload.append("folder/two/acton." + file.getKey(), file.getValue(), true);
            }
            var summary = service.finish(upload);
            assertThat(summary.layers()).hasSize(2);
            var page = service.features(summary.id(), 0, "acton", "TOWN", 0, 1);
            assertThat(page.total()).isEqualTo(1);
            assertThat(page.items().get(0).name()).isEqualTo("ACTON");
            assertThat(service.features(summary.id(), 0, "absent", "TOWN", 0, 25)
                            .items())
                    .isEmpty();
            assertThat(service.features(summary.id(), 0, "", "TOWN", 1, 25).items())
                    .isEmpty();
            assertThat(service.boundary(summary.id(), 0, page.items().get(0).id())
                            .problem())
                    .isNull();
            service.delete(summary.id());
            assertThatThrownBy(() -> service.features(summary.id(), 0, "", "", 0, 25))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void zipMatchesFolderAndSessionsExpire() throws Exception {
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-26T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        var service = new RegionImportService(clock);
        try (var upload = service.begin()) {
            var files = new LinkedHashMap<String, byte[]>();
            ShapefileReaderTest.acton().forEach((ext, bytes) -> files.put("towns/acton." + ext, bytes));
            upload.append("towns.zip", zip(files), true);
            var summary = service.finish(upload);
            assertThat(summary.layers()).hasSize(1);
            assertThat(service.boundary(summary.id(), 0, 0).rings().get(0)).hasSize(12);
            when(clock.instant()).thenReturn(now.plusSeconds(901));
            service.expire();
            assertThatThrownBy(() -> service.boundary(summary.id(), 0, 0))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("expired");
        }
    }

    @Test
    void rejectsTraversalDuplicatesAndReleasesUploadPermit() throws Exception {
        var service = new RegionImportService();
        try (var upload = service.begin()) {
            assertThatThrownBy(() -> upload.append("../town.shp", new byte[0], true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        try (var upload = service.begin()) {
            upload.append("town.shp", new byte[0], true);
            assertThatThrownBy(() -> upload.append("TOWN.SHP", new byte[0], true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        try (var upload = service.begin()) {
            upload.append("bad.zip", zip(Map.of("../town.shp", new byte[0])), true);
            assertThatThrownBy(() -> service.finish(upload))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Parent-relative");
        }
        try (var one = service.begin();
                var two = service.begin()) {
            assertThatThrownBy(service::begin).isInstanceOf(ResponseStatusException.class);
        }
        try (var available = service.begin()) {
            assertThat(available).isNotNull();
        }
    }
}
