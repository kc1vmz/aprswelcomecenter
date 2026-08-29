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

import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/http-packets")
public class HttpPacketController {
    private final String packetProcessorId = "HTTP";

    @Autowired
    private StationPacketQueue stationPacketQueue;

    @PostMapping
    public Mono<ResponseEntity<Void>> accept(@RequestBody StationPacket stationPacket) {
        stationPacket.setPacketProcessorId(packetProcessorId);
        stationPacketQueue.offer(stationPacket);
        return Mono.just(ResponseEntity.accepted().build());
    }
}
