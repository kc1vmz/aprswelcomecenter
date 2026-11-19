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
package com.kc1vmz.aprswc.controller;

import com.kc1vmz.aprswc.controller.ApiErrorHandler.ErrorBody;
import com.kc1vmz.aprswc.importregion.RegionImportService;
import com.kc1vmz.aprswc.importregion.RegionImportService.FeaturePage;
import com.kc1vmz.aprswc.importregion.RegionImportService.ImportSummary;
import com.kc1vmz.aprswc.importregion.ShapefileReader.Boundary;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/region-imports")
public class RegionImportController {
    private final RegionImportService imports;

    public RegionImportController(RegionImportService imports) {
        this.imports = imports;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ImportSummary> upload(@RequestBody Flux<PartEvent> parts) {
        return Mono.using(
                imports::begin,
                upload -> parts.publishOn(Schedulers.boundedElastic(), 1)
                        .doOnNext(part -> {
                            try {
                                if (!(part instanceof FilePartEvent file))
                                    throw new IllegalArgumentException("Only uploaded files are accepted.");
                                byte[] bytes = new byte[part.content().readableByteCount()];
                                part.content().read(bytes);
                                upload.append(file.filename(), bytes, part.isLast());
                            } finally {
                                DataBufferUtils.release(part.content());
                            }
                        })
                        .doOnDiscard(PartEvent.class, event -> DataBufferUtils.release(event.content()))
                        .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                        .then(Mono.fromCallable(() -> imports.finish(upload)).subscribeOn(Schedulers.boundedElastic()))
                        .timeout(Duration.ofMinutes(2)),
                upload -> upload.close());
    }

    @GetMapping("/{id}/layers/{layer}/features")
    public Mono<FeaturePage> features(
            @PathVariable UUID id,
            @PathVariable int layer,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String label,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        return Mono.fromCallable(() -> imports.features(id, layer, query, label, offset, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}/layers/{layer}/features/{feature}")
    public Mono<Boundary> boundary(@PathVariable UUID id, @PathVariable int layer, @PathVariable int feature) {
        return Mono.fromCallable(() -> imports.boundary(id, layer, feature)).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        imports.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorBody> timeout() {
        return ResponseEntity.status(408)
                .body(new ErrorBody(
                        "IMPORT_TIMEOUT", List.of(), "Upload timed out. Try a smaller collection.", List.of()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IOException.class})
    public ResponseEntity<ErrorBody> badUpload(Exception error) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody(
                        "INVALID_UPLOAD",
                        List.of(),
                        error instanceof IOException
                                ? "The ZIP could not be read. Upload a valid, unencrypted ZIP."
                                : error.getMessage(),
                        List.of()));
    }
}
