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

import com.kc1vmz.aprswc.accessor.PointOfInterestService;
import com.kc1vmz.aprswc.accessor.PointOfInterestService.*;
import com.kc1vmz.aprswc.communication.CommunicationInstanceManager;
import com.kc1vmz.aprswc.database.PointOfInterestRepository;
import com.kc1vmz.aprswc.enumeration.WelcomeCenterStatus;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.processor.PointOfInterestBeaconProcessor;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:poi-api;DB_CLOSE_DELAY=-1")
@AutoConfigureWebTestClient
class PointOfInterestControllerTest {
    @Autowired
    WebTestClient client;

    @Autowired
    PointOfInterestService service;

    @Autowired
    PointOfInterestRepository repository;

    @MockitoBean
    CommunicationInstanceManager connections;

    @MockitoSpyBean
    PointOfInterestBeaconProcessor beacons;

    WelcomeCenter center(String callsign) {
        return client.post()
                .uri("/api/v1/welcome-centers")
                .bodyValue(Map.of(
                        "callsign",
                        callsign,
                        "ownerCallsign",
                        "N0OWN",
                        "latitude",
                        "4336.50N",
                        "longitude",
                        "07258.30W"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(WelcomeCenter.class)
                .returnResult()
                .getResponseBody();
    }

    String path(WelcomeCenter c) {
        return "/api/v1/welcome-centers/" + c.getId() + "/points-of-interest";
    }

    Edit edit(Long version, String name, boolean down) {
        return new Edit(version, name, "A local landmark", "43.60833", "-72.97167", "c", "/", down);
    }

    View create(WelcomeCenter c, String name, boolean down) {
        return client.post()
                .uri(path(c))
                .bodyValue(edit(null, name, down))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(View.class)
                .returnResult()
                .getResponseBody();
    }

    View update(WelcomeCenter c, View p, String name, boolean down) {
        return client.put()
                .uri(path(c) + "/" + p.id())
                .bodyValue(edit(p.version(), name, down))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(View.class)
                .returnResult()
                .getResponseBody();
    }

    Snapshot snapshot(View p) {
        return service.snapshot(repository.findById(p.id()).orElseThrow());
    }

    void status(WelcomeCenter c, WelcomeCenterStatus state) {
        client.patch()
                .uri("/api/v1/welcome-centers/" + c.getId() + "/status")
                .bodyValue(Map.of("expectedStatus", c.getStatus(), "status", state))
                .exchange()
                .expectStatus()
                .isOk();
        c.setStatus(state);
    }

    @Test
    void availabilityLifecyclePreservesFlagInvalidatesQueueAndRejectsStaleEdits() {
        var c = center("N1POI");
        var p = create(c, "Park1", false);
        assertThat(p.name()).isEqualTo("PARK1");
        assertThat(p.latitude()).isEqualTo("4336.50N");
        assertThat(p.longitude()).isEqualTo("07258.30W");
        var live = snapshot(p);
        assertThat(live.callsignFrom()).isEqualTo("N1POI");
        assertThat(service.canSend(live, true)).isTrue();
        var down = update(c, p, p.name(), true);
        assertThat(down.status()).isEqualTo("DOWN");
        assertThat(service.canSend(live, true)).isFalse();
        assertThat(service.canSend(live, false)).isTrue();
        status(c, WelcomeCenterStatus.CLOSED);
        var closed = service.list(c.getId()).getFirst();
        assertThat(closed.temporarilyUnavailable()).isTrue();
        assertThat(closed.downReasons()).containsExactly("Welcome Center closed", "Temporarily unavailable");
        client.put()
                .uri(path(c) + "/" + p.id())
                .bodyValue(edit(down.version(), "PARK1", false))
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        status(c, WelcomeCenterStatus.OPEN);
        var reopened = service.list(c.getId()).getFirst();
        assertThat(reopened.status()).isEqualTo("DOWN");
        var restored = update(c, reopened, "PARK1", false);
        assertThat(restored.status()).isEqualTo("UP");
        assertThat(service.canSend(live, false)).isFalse();
        assertThat(service.canSend(live, true)).isFalse();
        assertThat(service.canSend(snapshot(restored), true)).isTrue();
        verify(beacons, atLeastOnce())
                .afterCommit(argThat(e -> e.previous() != null && !e.current().up()));
        status(c, WelcomeCenterStatus.CLOSED);
        var createdClosed = create(c, "CLOSEDPOI", false);
        assertThat(createdClosed.status()).isEqualTo("DOWN");
        assertThat(update(c, createdClosed, "CLOSEDPOI", false).status()).isEqualTo("DOWN");
    }

    @Test
    void parentRenameAndDeletionWithdrawPreviousIdentityAndCascade() {
        var c = center("N2POI");
        var p = create(c, "Museum", false);
        var old = snapshot(p);
        c.setCallsign("N2NEW");
        client.put()
                .uri("/api/v1/welcome-centers/" + c.getId())
                .bodyValue(c)
                .exchange()
                .expectStatus()
                .isOk();
        var now = service.list(c.getId()).getFirst();
        var renamed = snapshot(now);
        assertThat(renamed.callsignFrom()).isEqualTo("N2NEW");
        assertThat(now.version()).isGreaterThan(p.version());
        assertThat(service.canSend(old, true)).isFalse();
        assertThat(service.canSend(old, false)).isTrue();
        verify(beacons).afterCommit(argThat(e -> old.equals(e.previous()) && renamed.equals(e.current())));
        client.delete()
                .uri("/api/v1/welcome-centers/" + c.getId())
                .exchange()
                .expectStatus()
                .isNoContent();
        assertThat(repository.findById(p.id())).isEmpty();
        assertThat(service.canSend(renamed, true)).isFalse();
        assertThat(service.canSend(renamed, false)).isTrue();
        verify(beacons).afterCommit(argThat(e -> renamed.equals(e.previous()) && e.current() == null));
    }

    @Test
    void enforcesGlobalNamesOwnershipAndVersionAndWithdrawsRenames() {
        var c = center("N3POI");
        var other = center("N4POI");
        var p = create(c, "Cafe", false);
        var old = snapshot(p);
        client.post()
                .uri(path(other))
                .bodyValue(edit(null, "cafe", false))
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        client.post()
                .uri(path(c))
                .bodyValue(edit(null, "n4poi", false))
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        client.post()
                .uri("/api/v1/welcome-centers")
                .bodyValue(Map.of("callsign", "CAFE"))
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        other.setCallsign("CAFE");
        client.put()
                .uri("/api/v1/welcome-centers/" + other.getId())
                .bodyValue(other)
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        client.put()
                .uri(path(other) + "/" + p.id())
                .bodyValue(edit(p.version(), "STOLEN", false))
                .exchange()
                .expectStatus()
                .isNotFound();
        client.delete()
                .uri(path(c) + "/" + p.id() + "?version=100")
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        var renamed = update(c, p, "Cafe2", false);
        assertThat(service.canSend(old, true)).isFalse();
        assertThat(service.canSend(old, false)).isTrue();
        var reused = create(other, "CAFE", false);
        assertThat(service.canSend(old, false)).isFalse();
        client.delete()
                .uri(path(c) + "/" + p.id() + "?version=" + renamed.version())
                .exchange()
                .expectStatus()
                .isNoContent();
        assertThat(repository.findById(reused.id())).isPresent();
        assertThat(repository.findById(p.id())).isEmpty();
    }

    @Test
    void validatesSenderCoordinatesSymbolsAndTextWithoutPersistingOrPublishing() {
        var invalid = center("TOOLONG9");
        client.post()
                .uri(path(invalid))
                .bodyValue(edit(null, "VALIDNAME", false))
                .exchange()
                .expectStatus()
                .isBadRequest();
        var c = center("N5POI");
        clearInvocations(beacons);
        for (var bad : List.of(
                edit(null, "BAD-NAME", false),
                edit(null, "TENLETTERS1", false),
                new Edit(null, "VALID", "x".repeat(41), "4336.50N", "07258.30W", "c", "/", false),
                new Edit(null, "VALID", "nonascii\u00e9", "4336.50N", "07258.30W", "c", "/", false),
                new Edit(null, "VALID", "", "4360.00N", "07258.30W", "c", "/", false),
                new Edit(null, "VALID", "", "43", "181", "c", "/", false),
                new Edit(null, "VALID", "", "43", "-72", "cc", "/", false),
                new Edit(null, "VALID", "", "43", "-72", "c", "?", false))) {
            client.post().uri(path(c)).bodyValue(bad).exchange().expectStatus().isBadRequest();
        }
        assertThat(service.list(c.getId())).isEmpty();
        verify(beacons, never()).afterCommit(any());
        var p = create(c, "BOUNDARY", true);
        client.put()
                .uri(path(c) + "/" + p.id())
                .bodyValue(new Edit(p.version(), p.name(), "", "9000.00N", "18000.00W", "c", "\\", true))
                .exchange()
                .expectStatus()
                .isOk();
        c.setCallsign("TOOLONG8");
        client.put()
                .uri("/api/v1/welcome-centers/" + c.getId())
                .bodyValue(c)
                .exchange()
                .expectStatus()
                .isBadRequest();
        assertThat(snapshot(p).callsignFrom()).isEqualTo("N5POI");
    }
}
