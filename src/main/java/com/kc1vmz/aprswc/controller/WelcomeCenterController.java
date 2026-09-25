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

import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeCenterStatusChange;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/welcome-centers")
public class WelcomeCenterController {
    @Autowired
    private WelcomeCenterAccessor accessor;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> routingError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", Objects.toString(error.getReason(), "Request failed")));
    }

    @GetMapping
    public Flux<WelcomeCenter> all() {
        return accessor.findAll();
    }

    @GetMapping("/{id}")
    public Mono<WelcomeCenter> one(@PathVariable UUID id) {
        return accessor.findById(id);
    }

    @PostMapping
    public Mono<ResponseEntity<WelcomeCenter>> create(@Valid @RequestBody WelcomeCenter value) {
        return accessor.create(value)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @PutMapping("/{id}")
    public Mono<WelcomeCenter> replace(@PathVariable UUID id, @Valid @RequestBody WelcomeCenter value) {
        return accessor.replace(id, value);
    }

    @PatchMapping("/{id}/status")
    public Mono<WelcomeCenter> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody WelcomeCenterStatusChange change) {
        return accessor.changeStatus(id, change);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return accessor.delete(id).thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}/regions")
    public Flux<WelcomeRegion> regions(@PathVariable UUID id) {
        return accessor.findRegions(id);
    }

    @GetMapping("/{id}/station-positions")
    public Flux<StationPosition> stationPositions(@PathVariable UUID id) {
        return accessor.findStationPositions(id);
    }

    @PostMapping("/{id}/regions")
    public Mono<ResponseEntity<WelcomeRegion>> addRegion(@PathVariable UUID id, @RequestBody WelcomeRegion region) {
        return accessor.addRegion(id, region)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @PutMapping("/{id}/regions/{regionId}")
    public Mono<WelcomeRegion> replaceRegion(
            @PathVariable UUID id, @PathVariable UUID regionId, @RequestBody WelcomeRegion region) {
        return accessor.replaceRegion(id, regionId, region);
    }

    @DeleteMapping("/{id}/regions/{regionId}")
    public Mono<ResponseEntity<Void>> deleteRegion(@PathVariable UUID id, @PathVariable UUID regionId) {
        return accessor.deleteRegion(id, regionId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
