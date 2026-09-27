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

import static com.kc1vmz.aprswc.importregion.ShapefileReader.check;
import static com.kc1vmz.aprswc.importregion.ShapefileReader.checkTime;

import com.kc1vmz.aprswc.importregion.ShapefileReader.Boundary;
import com.kc1vmz.aprswc.importregion.ShapefileReader.Feature;
import com.kc1vmz.aprswc.importregion.ShapefileReader.Layer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegionImportService {
    public static final int MAX_UPLOAD_BYTES = 64 * 1024 * 1024;
    public static final int MAX_EXPANDED_BYTES = 128 * 1024 * 1024;
    public static final int MAX_FILES = 256;
    private static final Set<String> EXTENSIONS = Set.of("shp", "shx", "dbf", "prj", "cpg");
    private final Map<UUID, Session> sessions;
    private final Semaphore uploads;
    private final Clock clock;

    public RegionImportService() {
        this(Clock.systemUTC());
    }

    RegionImportService(Clock clock) {
        this.clock = clock;
        this.sessions = new HashMap<>();
        this.uploads = new Semaphore(2);
    }

    public record LayerSummary(
            int id,
            String name,
            String sourceCrs,
            String warning,
            String problem,
            List<String> fields,
            int featureCount) {}

    public record ImportSummary(UUID id, Instant expiresAt, List<LayerSummary> layers) {}

    public record FeatureSummary(
            int id,
            String name,
            Map<String, String> attributes,
            int vertices,
            int components,
            int holes,
            String problem) {}

    public record FeaturePage(int total, int offset, List<FeatureSummary> items) {}

    private record Session(Instant expiresAt, List<Layer> layers) {}

    public Upload begin() {
        if (!uploads.tryAcquire())
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Two uploads are already being processed. Try again shortly.");
        return new Upload(uploads);
    }

    /** Owns streamed request buffers only; nothing is written to the filesystem. */
    public static final class Upload implements AutoCloseable {
        private final Semaphore permit;
        private final Map<String, byte[]> files;
        private final Set<String> seen;
        private ByteArrayOutputStream current;
        private String name;
        private long bytes;
        private boolean closed;

        private Upload(Semaphore permit) {
            this.permit = permit;
            this.files = new LinkedHashMap<>();
            this.seen = new HashSet<>();
            this.current = null;
            this.name = null;
            this.bytes = 0;
            this.closed = false;
        }

        public void append(String filename, byte[] content, boolean last) {
            if (name == null) {
                name = safeName(filename);
                check(seen.add(name), "Duplicate uploaded filename: " + name);
                check(seen.size() <= MAX_FILES, "Upload at most 256 files.");
                current = new ByteArrayOutputStream();
            } else check(name.equals(safeName(filename)), "Unexpected multipart file sequence.");
            bytes += content.length;
            check(bytes <= MAX_UPLOAD_BYTES, "Upload exceeds the 64 MiB limit.");
            if (supported(name) || name.endsWith(".zip")) current.writeBytes(content);
            if (last) {
                if (supported(name) || name.endsWith(".zip")) files.put(name, current.toByteArray());
                current = null;
                name = null;
            }
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                files.clear();
                current = null;
                permit.release();
            }
        }
    }

    public ImportSummary finish(Upload upload) throws IOException {
        long deadline = System.nanoTime() + 30_000_000_000L;
        check(upload.name == null, "Incomplete file upload.");
        Map<String, byte[]> files = new LinkedHashMap<>(upload.files);
        List<String> archives =
                files.keySet().stream().filter(n -> n.endsWith(".zip")).toList();
        if (!archives.isEmpty()) {
            check(
                    archives.size() == 1 && upload.seen.size() == 1,
                    "Upload one ZIP file or a folder of companion files, not both.");
            files = unzip(files.get(archives.get(0)), deadline);
        }
        Map<String, Map<String, byte[]>> groups = new LinkedHashMap<>();
        for (var entry : files.entrySet()) {
            String name = entry.getKey();
            if (!supported(name)) continue;
            int dot = name.lastIndexOf('.');
            groups.computeIfAbsent(name.substring(0, dot), key -> new HashMap<>())
                    .put(name.substring(dot + 1), entry.getValue());
        }
        List<Layer> layers = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            if (!entry.getValue().containsKey("shp")) continue;
            check(layers.size() < 32, "Upload at most 32 shapefile layers.");
            checkTime(deadline);
            layers.add(ShapefileReader.read(layers.size(), entry.getKey(), entry.getValue(), deadline));
        }
        checkTime(deadline);
        check(!layers.isEmpty(), "No .shp layers found. Include .shp, .shx, .dbf and .prj companion files.");
        return save(layers);
    }

    private synchronized ImportSummary save(List<Layer> layers) {
        expire();
        if (sessions.size() >= 4)
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Four imports are already open. Close one or wait for it to expire.");
        UUID id = UUID.randomUUID();
        Instant expiry = clock.instant().plusSeconds(900);
        sessions.put(id, new Session(expiry, List.copyOf(layers)));
        return new ImportSummary(
                id,
                expiry,
                layers.stream()
                        .map(layer -> new LayerSummary(
                                layer.id(),
                                layer.name(),
                                layer.sourceCrs(),
                                layer.warning(),
                                layer.problem(),
                                layer.fields(),
                                layer.features().size()))
                        .toList());
    }

    public FeaturePage features(UUID id, int layerId, String query, String label, int offset, int limit) {
        check(offset >= 0 && limit > 0 && limit <= 100 && query.length() <= 200, "Invalid search or page size.");
        Layer layer = layer(id, layerId);
        check(label.isEmpty() || layer.fields().contains(label), "Unknown label field.");
        String term = query.toLowerCase(Locale.ROOT);
        List<Feature> matches = layer.features().stream()
                .filter(feature -> feature.attributes().values().stream()
                        .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(term)))
                .toList();
        return new FeaturePage(
                matches.size(),
                offset,
                matches.stream()
                        .skip(offset)
                        .limit(limit)
                        .map(feature -> new FeatureSummary(
                                feature.id(),
                                label.isEmpty()
                                        ? "Feature " + (feature.id() + 1)
                                        : feature.attributes().get(label),
                                feature.attributes(),
                                feature.vertices(),
                                feature.outerRings(),
                                feature.holes(),
                                layer.problem() == null ? feature.problem() : layer.problem()))
                        .toList());
    }

    public Boundary boundary(UUID id, int layerId, int featureId) {
        Layer layer = layer(id, layerId);
        Feature feature = layer.features().stream()
                .filter(f -> f.id() == featureId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found."));
        return ShapefileReader.boundary(layer, feature, System.nanoTime() + 30_000_000_000L);
    }

    private synchronized Layer layer(UUID id, int layerId) {
        expire();
        Session session = sessions.get(id);
        if (session == null)
            throw new ResponseStatusException(
                    HttpStatus.GONE, "Import expired or was closed. Upload the collection again.");
        if (layerId < 0 || layerId >= session.layers.size())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layer not found.");
        return session.layers.get(layerId);
    }

    public synchronized void delete(UUID id) {
        sessions.remove(id);
    }

    @Scheduled(fixedDelay = 60000)
    public synchronized void expire() {
        sessions.values().removeIf(session -> !session.expiresAt.isAfter(clock.instant()));
    }

    private static Map<String, byte[]> unzip(byte[] zip, long deadline) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        long expanded = 0;
        int count = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                checkTime(deadline);
                check(++count <= MAX_FILES, "ZIP contains more than 256 entries.");
                String name = safeName(entry.getName());
                check(seen.add(name), "ZIP contains duplicate filenames: " + name);
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    checkTime(deadline);
                    expanded += read;
                    check(expanded <= MAX_EXPANDED_BYTES, "Expanded ZIP exceeds the 128 MiB limit.");
                    if (supported(name)) bytes.write(buffer, 0, read);
                }
                if (supported(name)) files.put(name, bytes.toByteArray());
            }
        }
        return files;
    }

    private static boolean supported(String name) {
        return EXTENSIONS.contains(name.substring(name.lastIndexOf('.') + 1));
    }

    private static String safeName(String filename) {
        check(filename != null && !filename.isBlank() && filename.length() <= 512, "Invalid uploaded filename.");
        String name = filename.replace('\\', '/').toLowerCase(Locale.ROOT);
        check(
                !name.startsWith("/") && name.indexOf(':') < 0 && name.chars().noneMatch(c -> c < 32),
                "Absolute or invalid upload paths are not allowed.");
        for (String segment : name.split("/"))
            check(!segment.equals("..") && !segment.equals("."), "Parent-relative upload paths are not allowed.");
        return name;
    }
}
