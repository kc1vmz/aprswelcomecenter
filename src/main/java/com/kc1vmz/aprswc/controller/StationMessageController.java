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

import com.kc1vmz.aprswc.accessor.StationMessageAccessor;
import com.kc1vmz.aprswc.object.StationMessage;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/station-messages")
public class StationMessageController {
    @Autowired
    private StationMessageAccessor accessor;

    @GetMapping
    public Flux<StationMessage> all(
            @RequestParam(required = false) String callsignTo, @RequestParam(required = false) UUID welcomeCenterId) {
        if (welcomeCenterId != null) {
            return accessor.findAllByWelcomeCenterId(welcomeCenterId);
        }
        return callsignTo == null ? accessor.findAll() : accessor.findAllByCallsignTo(callsignTo);
    }

    @GetMapping("/{id}")
    public Mono<StationMessage> one(@PathVariable UUID id) {
        return accessor.findById(id);
    }

    @PostMapping
    public Mono<ResponseEntity<StationMessage>> create(@RequestBody StationMessage value) {
        return accessor.create(value)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @PutMapping("/{id}")
    public Mono<StationMessage> replace(@PathVariable UUID id, @RequestBody StationMessage value) {
        return accessor.replace(id, value);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return accessor.delete(id).thenReturn(ResponseEntity.noContent().build());
    }

    @DeleteMapping
    public Mono<ResponseEntity<Void>> deleteAll(
            @RequestParam(required = false) String callsignTo, @RequestParam(required = false) UUID welcomeCenterId) {
        if ((callsignTo == null) == (welcomeCenterId == null)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Specify exactly one of callsignTo or welcomeCenterId"));
        }
        Mono<Void> deletion = welcomeCenterId == null
                ? accessor.deleteAllByCallsignTo(callsignTo)
                : accessor.deleteAllByWelcomeCenterId(welcomeCenterId);
        return deletion.thenReturn(ResponseEntity.noContent().build());
    }
}
