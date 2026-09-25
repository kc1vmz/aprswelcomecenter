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
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.communication.*;
import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.object.*;
import com.kc1vmz.aprswc.processor.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:center-routing;DB_CLOSE_DELAY=-1")
@AutoConfigureWebTestClient
class CenterCommunicationRoutingTest {
    @Autowired
    WebTestClient client;

    @Autowired
    CenterCommunicationRouting routing;

    @Autowired
    WelcomeCenterRepository centers;

    @Autowired
    CommunicationInstanceRepository instances;

    @MockitoBean
    CommunicationInstanceManager manager;

    @MockitoSpyBean
    ObjectBeaconQueue beacons;

    private CommunicationInstance connection() {
        return client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(Map.of("type", "KISS_TCP", "state", "PAUSED", "host", "localhost", "port", 8001))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(CommunicationInstance.class)
                .returnResult()
                .getResponseBody();
    }

    private WelcomeCenter center(String callsign) {
        return client.post()
                .uri("/api/v1/welcome-centers")
                .bodyValue(Map.of("callsign", callsign, "latitude", "4336.50N", "longitude", "07258.30W"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(WelcomeCenter.class)
                .returnResult()
                .getResponseBody();
    }

    private WelcomeCenter save(WelcomeCenter c) {
        return client.put()
                .uri("/api/v1/welcome-centers/" + c.getId())
                .bodyValue(c)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WelcomeCenter.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void defaultsAllSupportsEmptySelectionAndIncludesFutureInstances() {
        var c = center("N1ROUT");
        var a = connection();
        assertThat(c.getCommunicationMode()).isEqualTo("ALL");
        assertThat(routing.permits(c.getId(), a.getId().toString())).isTrue();
        c.setCommunicationMode("SELECTED");
        c.setCommunicationInstanceIds(Set.of());
        c = save(c);
        assertThat(routing.permits(c.getId(), a.getId().toString())).isFalse();
        var b = connection();
        assertThat(routing.permits(c.getId(), b.getId().toString())).isFalse();
        c.setCommunicationMode("ALL");
        c = save(c);
        assertThat(routing.permits(c.getId(), a.getId().toString())).isTrue();
        assertThat(routing.permits(c.getId(), b.getId().toString())).isTrue();
        var future = connection();
        assertThat(routing.permits(c.getId(), future.getId().toString())).isTrue();
    }

    @Test
    void deletionKeepsSelectedModeBumpsVersionAndRejectsStaleAndDeletedSelections() {
        var a = connection();
        var c = center("N2ROUT");
        c.setCommunicationMode("SELECTED");
        c.setCommunicationInstanceIds(Set.of(a.getId()));
        c = save(c);
        assertThat(c.getCommunicationInstanceIds()).containsExactly(a.getId());
        client.delete()
                .uri("/api/v1/communication-instances/" + a.getId() + "?version=" + a.getVersion())
                .exchange()
                .expectStatus()
                .isNoContent();
        var now = centers.findById(c.getId()).orElseThrow();
        assertThat(now.getCommunicationMode()).isEqualTo("SELECTED");
        assertThat(now.getCommunicationInstanceIds()).isEmpty();
        assertThat(now.getRoutingVersion()).isGreaterThan(c.getRoutingVersion());
        client.put()
                .uri("/api/v1/welcome-centers/" + c.getId())
                .bodyValue(c)
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        now.setCommunicationInstanceIds(Set.of(a.getId()));
        client.put()
                .uri("/api/v1/welcome-centers/" + c.getId())
                .bodyValue(now)
                .exchange()
                .expectStatus()
                .isEqualTo(409);
    }

    @Test
    void selectingNewRoutesBeaconsCenterAndAvailablePoisOnlyAndRemovingDoesNotWithdraw() {
        var a = connection();
        var b = connection();
        var c = center("N3ROUT");
        for (boolean down : List.of(false, true))
            client.post()
                    .uri("/api/v1/welcome-centers/" + c.getId() + "/points-of-interest")
                    .bodyValue(Map.of(
                            "name",
                            down ? "DOWNPOI" : "UPPOI",
                            "description",
                            "Local",
                            "latitude",
                            "4336.50N",
                            "longitude",
                            "07258.30W",
                            "symbolCode",
                            "c",
                            "symbolTableId",
                            "/",
                            "temporarilyUnavailable",
                            down))
                    .exchange()
                    .expectStatus()
                    .isCreated();
        clearInvocations(beacons);
        c.setCommunicationMode("SELECTED");
        c.setCommunicationInstanceIds(Set.of(a.getId()));
        c = save(c);
        verify(beacons, never()).offer(any());
        clearInvocations(manager);
        c.setCommunicationInstanceIds(Set.of(a.getId(), b.getId()));
        c = save(c);
        var capture = org.mockito.ArgumentCaptor.forClass(ObjectBeacon.class);
        verify(beacons, times(2)).offer(capture.capture());
        assertThat(capture.getAllValues())
                .extracting(ObjectBeacon::getObjectName)
                .containsExactlyInAnyOrder("N3ROUT", "UPPOI");
        for (var beacon : capture.getAllValues()) {
            assertThat(beacon.isActive()).isTrue();
            assertThat(beacon.getTargetInstanceIds()).containsExactly(b.getId());
            assertThat(beacon.getCommunicationScope().centerId()).isEqualTo(c.getId());
        }
        verify(manager, never()).reconcile();
        clearInvocations(beacons);
        c.setCommunicationInstanceIds(Set.of());
        save(c);
        verify(beacons, never()).offer(any());
    }

    @Test
    void staleQueuedScopesUseCurrentSelectionAndParentDeletionKeepsOnlySnapshotWithdrawalRoutes() {
        var a = connection();
        var b = connection();
        var c = center("N4ROUT");
        var old = CommunicationScope.of(c);
        c.setCommunicationMode("SELECTED");
        c.setCommunicationInstanceIds(Set.of(a.getId()));
        c = save(c);
        assertThat(routing.permits(old, b.getId().toString(), false)).isFalse();
        assertThat(routing.permits(old, a.getId().toString(), false)).isTrue();
        var scope = CommunicationScope.of(c);
        client.delete()
                .uri("/api/v1/welcome-centers/" + c.getId())
                .exchange()
                .expectStatus()
                .isNoContent();
        assertThat(instances.existsById(a.getId())).isTrue();
        assertThat(routing.permits(scope, a.getId().toString(), false)).isFalse();
        assertThat(routing.permits(scope, a.getId().toString(), true)).isTrue();
        assertThat(routing.permits(scope, b.getId().toString(), true)).isFalse();
    }
}
