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
package com.kc1vmz.aprswc.accessor;

import com.kc1vmz.aprswc.database.StationMessageRepository;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.processor.StationMessageQueue;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class StationMessageAccessor {
    @Autowired
    private StationMessageRepository repository;

    @Autowired
    private StationMessageQueue stationMessageQueue;

    public Flux<StationMessage> findAll() {
        return Mono.fromCallable(repository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Flux<StationMessage> findAllByCallsignTo(String callsignTo) {
        return Mono.fromCallable(() -> repository.findAllByCallsignToIgnoreCaseOrderBySentTimeDesc(callsignTo))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Flux<StationMessage> findAllByWelcomeCenterId(UUID welcomeCenterId) {
        return Mono.fromCallable(() -> repository.findAllByWelcomeCenterIdOrderBySentTimeDesc(welcomeCenterId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<StationMessage> findById(UUID id) {
        return Mono.fromCallable(() -> repository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<StationMessage> create(StationMessage value) {
        value.setId(null);
        return Mono.fromCallable(() -> repository.save(value))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(stationMessageQueue::offer);
    }

    public Mono<StationMessage> replace(UUID id, StationMessage value) {
        return findById(id)
                .then(Mono.fromCallable(() -> {
                            value.setId(id);
                            return repository.save(value);
                        })
                        .subscribeOn(Schedulers.boundedElastic()))
                .doOnNext(stationMessageQueue::offer);
    }

    /** Persists changes made while processing a message without placing it back on the processing queue. */
    public Mono<StationMessage> saveProcessedMessage(StationMessage value) {
        return Mono.fromCallable(() -> repository.save(value)).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> delete(UUID id) {
        return findById(id)
                .then(Mono.fromRunnable(() -> repository.deleteById(id)).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    public Mono<Void> deleteAllByCallsignTo(String callsignTo) {
        return Mono.fromCallable(() -> repository.deleteAllByCallsignToIgnoreCase(callsignTo))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<Void> deleteAllByWelcomeCenterId(UUID welcomeCenterId) {
        return Mono.fromCallable(() -> repository.deleteAllByWelcomeCenterId(welcomeCenterId))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
