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

import static org.assertj.core.api.Assertions.*;

import com.kc1vmz.aprswc.accessor.ContainmentDeletionService;
import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.enumeration.*;
import com.kc1vmz.aprswc.object.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ContainmentDeletionControllerTest {
    @Autowired
    WebTestClient client;

    @Autowired
    WelcomeCenterRepository centers;

    @Autowired
    WelcomeRegionRepository regions;

    @Autowired
    CommunicationCategoryRepository categories;

    @Autowired
    CommunicationPolicyRepository policies;

    @Autowired
    AnnouncementRepository announcements;

    @Autowired
    CommunicationEventRepository events;

    @Autowired
    StationCommandRepository commands;

    @Autowired
    StationMessageRepository messages;

    @Autowired
    StationRepository stations;

    @Autowired
    StationPositionRepository positions;

    @Autowired
    ContainmentDeletionService deletions;

    @Autowired
    TransactionTemplate transactions;

    @BeforeEach
    @AfterEach
    void clean() {
        announcements.deleteAll();
        events.deleteAll();
        commands.deleteAll();
        messages.deleteAll();
        policies.deleteAll();
        centers.deleteAll();
        categories.deleteAll();
        stations.deleteAll();
    }

    private WelcomeCenter center(String callsign) {
        WelcomeCenter center = new WelcomeCenter(
                null, callsign, "Test", callsign, null, null, null, null, null, null, null, null, "c", "/", List.of());
        center.addRegion(new WelcomeRegion(
                null,
                "Region",
                "Test",
                RegionType.CIRCLE,
                null,
                null,
                null,
                null,
                "07106.00W",
                "4218.00N",
                10,
                DistanceUnit.MILES));
        return centers.saveAndFlush(center);
    }

    private CommunicationCategory category(String name) {
        return categories.saveAndFlush(new CommunicationCategory(null, name, "Test", false, false));
    }

    private CommunicationPolicy policy(WelcomeCenter center, CommunicationCategory category) {
        CommunicationPolicy policy = policies.saveAndFlush(new CommunicationPolicy(
                null, category, center, "Hello", null, null, MessageType.MESSAGE, CommunicationEventType.ENTER_REGION));
        announcements.saveAndFlush(new Announcement(null, center, policy, null));
        announcements.saveAndFlush(new Announcement(null, center, policy, LocalDateTime.now()));
        events.saveAndFlush(new CommunicationEvent(
                null, center, CommunicationEventType.ENTER_REGION, policy.getId(), "N1TEST", null));
        return policy;
    }

    private void delete(String resource, UUID id) {
        client.delete()
                .uri("/api/v1/" + resource + "/{id}", id)
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    void deletesCenterWithEveryChildAndPreservesOtherCenterAndSharedCategory() {
        var category = category("Shared");
        var removed = center("N1DEL");
        var kept = center("N1KEEP");
        policy(removed, category);
        var keptPolicy = policy(kept, category);
        commands.saveAndFlush(new StationCommand(null, "N1TEST", removed, null, "HELP", StationCommandType.UNKNOWN));
        var keptCommand = commands.saveAndFlush(
                new StationCommand(null, "N1TEST", kept, null, "HELP", StationCommandType.UNKNOWN));
        messages.saveAndFlush(
                new StationMessage(null, "N1TEST", "N1DEL", removed, null, "Hello", null, MessageType.MESSAGE));
        var keptMessage = messages.saveAndFlush(
                new StationMessage(null, "N1TEST", "N1KEEP", kept, null, "Hello", null, MessageType.MESSAGE));

        delete("welcome-centers", removed.getId());

        assertThat(centers.findAll()).extracting(WelcomeCenter::getId).containsExactly(kept.getId());
        assertThat(regions.count()).isEqualTo(1);
        assertThat(policies.findAll()).extracting(CommunicationPolicy::getId).containsExactly(keptPolicy.getId());
        assertThat(announcements.count()).isEqualTo(2);
        assertThat(events.count()).isEqualTo(1);
        assertThat(commands.findAll()).extracting(StationCommand::getId).containsExactly(keptCommand.getId());
        assertThat(messages.findAll()).extracting(StationMessage::getId).containsExactly(keptMessage.getId());
        assertThat(categories.existsById(category.getId())).isTrue();
    }

    @Test
    void deletesPolicyWithAnnouncementsAndContextEventsButPreservesSiblings() {
        var center = center("N1TEST");
        var category = category("Shared");
        var removed = policy(center, category);
        var kept = policy(center, category);
        delete("communication-policies", removed.getId());
        assertThat(policies.findAll()).extracting(CommunicationPolicy::getId).containsExactly(kept.getId());
        assertThat(announcements.count()).isEqualTo(2);
        assertThat(events.findAll()).extracting(CommunicationEvent::getContext).containsExactly(kept.getId());
        assertThat(centers.existsById(center.getId())).isTrue();
        assertThat(categories.existsById(category.getId())).isTrue();
    }

    @Test
    void deletesCategoryAndItsPoliciesAcrossCenters() {
        var first = center("N1FIRST");
        var second = center("N1SECOND");
        var removed = category("Removed");
        var kept = category("Kept");
        policy(first, removed);
        policy(second, removed);
        var keptPolicy = policy(first, kept);
        delete("communication-categories", removed.getId());
        assertThat(categories.findAll())
                .extracting(CommunicationCategory::getId)
                .containsExactly(kept.getId());
        assertThat(policies.findAll()).extracting(CommunicationPolicy::getId).containsExactly(keptPolicy.getId());
        assertThat(announcements.count()).isEqualTo(2);
        assertThat(events.count()).isEqualTo(1);
        assertThat(centers.count()).isEqualTo(2);
    }

    @Test
    void rollsBackParentAndChildrenTogether() {
        var center = center("N1TEST");
        var policy = policy(center, category("Test"));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    deletions.deleteWelcomeCenter(center.getId());
                    throw new IllegalStateException("force rollback after deletes flush");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(centers.existsById(center.getId())).isTrue();
        assertThat(policies.existsById(policy.getId())).isTrue();
        assertThat(announcements.count()).isEqualTo(2);
        assertThat(events.count()).isEqualTo(1);
        assertThat(regions.count()).isEqualTo(1);
    }

    @Test
    void missingParentsReturnNotFound() {
        for (String resource : List.of("welcome-centers", "communication-policies", "communication-categories")) {
            client.delete()
                    .uri("/api/v1/" + resource + "/{id}", UUID.randomUUID())
                    .exchange()
                    .expectStatus()
                    .isNotFound();
        }
    }

    @Test
    void deletesOnePositionAndRejectsWrongParent() {
        var first = new Station(null, "N1FIRST", StationState.UNKNOWN, List.of());
        first.addPosition(new StationPosition(null, "N1FIRST", "07106.00W", "4218.00N", LocalDateTime.now()));
        first.addPosition(new StationPosition(null, "N1FIRST", "07106.01W", "4218.01N", LocalDateTime.now()));
        first = stations.saveAndFlush(first);
        var second = stations.saveAndFlush(new Station(null, "N1SECOND", StationState.UNKNOWN, List.of()));
        var positionId = positions.findAllByStationId(first.getId()).getFirst().getId();
        client.delete()
                .uri("/api/v1/stations/{id}/positions/{positionId}", second.getId(), positionId)
                .exchange()
                .expectStatus()
                .isNotFound();
        assertThat(positions.count()).isEqualTo(2);
        client.delete()
                .uri("/api/v1/stations/{id}/positions/{positionId}", first.getId(), positionId)
                .exchange()
                .expectStatus()
                .isNoContent();
        assertThat(positions.existsById(positionId)).isFalse();
        assertThat(positions.count()).isEqualTo(1);
        assertThat(stations.count()).isEqualTo(2);
    }
}
