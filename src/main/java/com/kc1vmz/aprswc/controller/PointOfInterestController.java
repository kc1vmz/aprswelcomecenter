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

import com.kc1vmz.aprswc.accessor.PointOfInterestService;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/welcome-centers/{centerId}/points-of-interest")
public class PointOfInterestController {
    private final PointOfInterestService service;

    public PointOfInterestController(PointOfInterestService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<List<PointOfInterestService.View>> list(@PathVariable UUID centerId) {
        return Mono.fromCallable(() -> service.list(centerId)).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<PointOfInterestService.View>> create(
            @PathVariable UUID centerId, @RequestBody PointOfInterestService.Edit value) {
        return Mono.fromCallable(() -> ResponseEntity.status(201).body(service.save(centerId, null, value)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{id}")
    public Mono<PointOfInterestService.View> update(
            @PathVariable UUID centerId, @PathVariable UUID id, @RequestBody PointOfInterestService.Edit value) {
        return Mono.fromCallable(() -> service.save(centerId, id, value)).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable UUID centerId, @PathVariable UUID id, @RequestParam long version) {
        return Mono.fromCallable(() -> {
                    service.delete(centerId, id, version);
                    return ResponseEntity.noContent().<Void>build();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", Objects.toString(error.getReason(), "Request failed")));
    }
}
