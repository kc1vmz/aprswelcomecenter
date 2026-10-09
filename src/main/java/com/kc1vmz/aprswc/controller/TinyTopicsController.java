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

import com.fasterxml.jackson.databind.JsonNode;
import com.kc1vmz.aprswc.content.TinyTopicsClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class TinyTopicsController {
    private final TinyTopicsClient client;

    public TinyTopicsController(TinyTopicsClient client) {
        this.client = client;
    }

    @GetMapping("/api/v1/tiny-topics/topics")
    public Mono<JsonNode> topics() {
        return Mono.fromCallable(client::topics)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(error -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No topics available", error));
    }
}
