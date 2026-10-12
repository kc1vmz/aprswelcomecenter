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

import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ApplicationSettingsAccessor {
    private final ApplicationSettingsRepository repository;

    public ApplicationSettingsAccessor(ApplicationSettingsRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> setPacketLogging(boolean enabled) {
        return Mono.fromRunnable(() -> {
                    var settings = repository.findAll().stream()
                            .findFirst()
                            .orElseGet(() -> new ApplicationSettings(
                                    null, null, ApplicationSettings.DEFAULT_TINY_TOPICS_SERVER_URL));
                    settings.setPacketLoggingEnabled(enabled);
                    repository.save(settings);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Flux<ApplicationSettings> findAll() {
        return Mono.fromCallable(repository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<ApplicationSettings> findById(UUID id) {
        return Mono.fromCallable(() -> repository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<ApplicationSettings> create(ApplicationSettings value) {
        value.setId(null);
        Mono<ApplicationSettings> ret =
                Mono.fromCallable(() -> repository.save(value)).subscribeOn(Schedulers.boundedElastic());
        return ret;
    }

    public Mono<ApplicationSettings> replace(UUID id, ApplicationSettings value) {
        Mono<ApplicationSettings> ret = findById(id).flatMap(current -> Mono.fromCallable(() -> {
                    value.setId(id);
                    value.setPacketLoggingEnabled(current.isPacketLoggingEnabled());
                    return repository.save(value);
                })
                .subscribeOn(Schedulers.boundedElastic()));
        return ret;
    }

    public Mono<Void> delete(UUID id) {
        Mono<Void> ret = findById(id)
                .then(Mono.fromRunnable(() -> repository.deleteById(id)).subscribeOn(Schedulers.boundedElastic()))
                .then();
        return ret;
    }
}
