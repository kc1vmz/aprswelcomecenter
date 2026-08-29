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

import com.kc1vmz.aprswc.accessor.StationAccessor;
import com.kc1vmz.aprswc.object.IgnoreStation;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {
    @Autowired
    private StationAccessor accessor;

    @GetMapping
    public Flux<Station> all(@RequestParam(defaultValue = "true", value = "excludeIgnored") boolean excludeIgnored) {
        return accessor.findAll(excludeIgnored);
    }

    @GetMapping("/{id}")
    public Mono<Station> one(@PathVariable UUID id) {
        return accessor.findById(id);
    }

    @PostMapping
    public Mono<ResponseEntity<Station>> create(@Valid @RequestBody Station value) {
        return accessor.create(value)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @PutMapping("/{id}")
    public Mono<Station> replace(@PathVariable UUID id, @Valid @RequestBody Station value) {
        return accessor.replace(id, value);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return accessor.delete(id).thenReturn(ResponseEntity.noContent().build());
    }

    @DeleteMapping
    public Mono<ResponseEntity<Void>> deleteAll() {
        return accessor.deleteAll().thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/ignore")
    public Mono<IgnoreStation> ignore(@PathVariable UUID id) {
        return accessor.ignore(id);
    }

    @GetMapping("/{id}/positions")
    public Flux<StationPosition> positions(@PathVariable UUID id) {
        return accessor.findPositions(id);
    }

    @GetMapping("/{id}/welcome-centers")
    public Flux<WelcomeCenter> welcomeCenters(@PathVariable UUID id) {
        return accessor.findWelcomeCentersByLatestStationPosition(id);
    }

    @PostMapping("/{id}/positions")
    public Mono<ResponseEntity<StationPosition>> addPosition(
            @PathVariable UUID id, @RequestBody StationPosition position) {
        return accessor.addPosition(id, position)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @PutMapping("/{id}/positions/{positionId}")
    public Mono<StationPosition> replacePosition(
            @PathVariable UUID id, @PathVariable UUID positionId, @RequestBody StationPosition position) {
        return accessor.replacePosition(id, positionId, position);
    }

    @DeleteMapping("/{id}/positions/{positionId}")
    public Mono<ResponseEntity<Void>> deletePosition(@PathVariable UUID id, @PathVariable UUID positionId) {
        return accessor.deletePosition(id, positionId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{id}/positions")
    public Mono<ResponseEntity<Void>> deleteAllPositions(@PathVariable UUID id) {
        return accessor.deleteAllPositions(id)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
