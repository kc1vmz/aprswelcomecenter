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

import com.kc1vmz.aprswc.accessor.StationPacketAccessor;
import com.kc1vmz.aprswc.object.StationPacket;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/station-packets")
public class StationPacketController {
    @Autowired
    private StationPacketAccessor accessor;

    @GetMapping
    public Flux<StationPacket> all() {
        return accessor.findAll();
    }

    @GetMapping(value = "/csv", produces = "text/csv")
    public Mono<ResponseEntity<String>> download() {
        return accessor.exportCsv().map(csv -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("station-packets.csv")
                                .build()
                                .toString())
                .body(csv));
    }

    @GetMapping("/{id}")
    public Mono<StationPacket> one(@PathVariable UUID id) {
        return accessor.findById(id);
    }

    @PostMapping
    public Mono<ResponseEntity<StationPacket>> create(@RequestBody StationPacket value) {
        return accessor.create(value)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @PutMapping("/{id}")
    public Mono<StationPacket> replace(@PathVariable UUID id, @RequestBody StationPacket value) {
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
}
