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

import com.kc1vmz.aprswc.content.PolicyContentService;
import com.kc1vmz.aprswc.database.CommunicationCategoryRepository;
import com.kc1vmz.aprswc.database.CommunicationPolicyRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import com.kc1vmz.aprswc.processor.PolicySchedule;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class CommunicationPolicyAccessor {
    private final CommunicationPolicyRepository repository;
    private final ContainmentDeletionService deletions;
    private final CommunicationCategoryRepository categories;
    private final WelcomeCenterRepository centers;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public CommunicationPolicyAccessor(
            CommunicationPolicyRepository repository,
            ContainmentDeletionService deletions,
            CommunicationCategoryRepository categories,
            WelcomeCenterRepository centers,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.deletions = deletions;
        this.categories = categories;
        this.centers = centers;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
    }

    public Flux<CommunicationPolicy> findAll() {
        return Mono.fromCallable(repository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Flux<CommunicationPolicy> findByWelcomeCenterId(UUID welcomeCenterId) {
        return Mono.fromCallable(() -> repository.findByWelcomeCenterId(welcomeCenterId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<CommunicationPolicy> findById(UUID id) {
        return Mono.fromCallable(() -> repository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<CommunicationPolicy> create(CommunicationPolicy value) {
        return save(null, value);
    }

    public Mono<CommunicationPolicy> replace(UUID id, CommunicationPolicy value) {
        return save(id, value);
    }

    private Mono<CommunicationPolicy> save(UUID id, CommunicationPolicy value) {
        return Mono.fromCallable(() -> transaction.execute(tx -> {
                    jdbc.queryForObject("select id from aprs_object_name_lock where id=1 for update", Integer.class);
                    CommunicationPolicy old = id == null
                            ? null
                            : repository
                                    .findById(id)
                                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                    if (old != null && old.getVersion() != value.getVersion())
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Policy changed. Close and reopen the editor.");
                    if (value.getWelcomeCenter() == null
                            || value.getWelcomeCenter().getId() == null
                            || value.getCategory() == null
                            || value.getCategory().getId() == null)
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Welcome Center and category are required");
                    if (old != null
                            && !Objects.equals(
                                    old.getWelcomeCenter().getId(),
                                    value.getWelcomeCenter().getId()))
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Cannot move a policy to another Welcome Center");
                    value.setWelcomeCenter(
                            centers.findById(value.getWelcomeCenter().getId())
                                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
                    value.setCategory(categories
                            .findById(value.getCategory().getId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
                    if (PolicySchedule.automated(value) && value.getCategory().isAutoGeneratedText())
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Choose a category with explicit message text");
                    PolicyContentService.validate(value);
                    Instant now = Instant.now();
                    PolicySchedule.validate(value, old, now);
                    boolean reset = old == null || !PolicySchedule.sameSchedule(old, value);
                    value.setId(id);
                    if (id == null) value.setVersion(0);
                    var saved = repository.saveAndFlush(value);
                    if (reset) {
                        Instant next = PolicySchedule.next(saved, now);
                        jdbc.update(
                                "update communication_policies set next_run_at=? where id=?",
                                next == null ? null : Timestamp.from(next),
                                saved.getId());
                    }
                    return saved;
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> delete(UUID id) {
        return Mono.fromRunnable(() -> deletions.deletePolicy(id))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
