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

import com.kc1vmz.aprswc.communication.CommunicationInstanceService;
import com.kc1vmz.aprswc.object.CommunicationInstance;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/communication-instances")
public class CommunicationInstanceController {
    private final CommunicationInstanceService service;

    public CommunicationInstanceController(CommunicationInstanceService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<List<CommunicationInstanceService.View>> all() {
        return Mono.fromCallable(service::list).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<CommunicationInstance> create(@RequestBody CommunicationInstance value) {
        return Mono.fromCallable(() -> service.save(null, value)).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{id}")
    public Mono<CommunicationInstance> replace(@PathVariable UUID id, @RequestBody CommunicationInstance value) {
        return Mono.fromCallable(() -> service.save(id, value)).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id, @RequestParam long version) {
        return Mono.fromCallable(() -> {
                    service.delete(id, version);
                    return ResponseEntity.noContent().<Void>build();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failure(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", Objects.toString(e.getReason(), "Request failed")));
    }
}
