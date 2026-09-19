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

import com.kc1vmz.aprswc.accessor.WelcomeCenterLifecycle;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.enumeration.WelcomeCenterStatus;
import com.kc1vmz.aprswc.object.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:center-status;DB_CLOSE_DELAY=-1")
@AutoConfigureWebTestClient
class WelcomeCenterStatusControllerTest {
    @Autowired
    WebTestClient client;

    @Autowired
    WelcomeCenterRepository centers;

    @MockitoSpyBean
    WelcomeCenterLifecycle lifecycle;

    private WelcomeCenter create(String callsign) {
        return client.post()
                .uri("/api/v1/welcome-centers")
                .bodyValue(Map.of("callsign", callsign))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(WelcomeCenter.class)
                .returnResult()
                .getResponseBody();
    }

    private void update(WelcomeCenter center) {
        client.put()
                .uri("/api/v1/welcome-centers/{id}", center.getId())
                .bodyValue(center)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void defaultsOpenAndOnlyCallsHooksForCommittedTransitions() {
        WelcomeCenter center = create("N1STATUS");
        assertThat(center.getStatus()).isEqualTo(WelcomeCenterStatus.OPEN);
        verify(lifecycle, never()).onWelcomeCenterOpened(any(), any());
        center.setStatus(WelcomeCenterStatus.CLOSED);
        update(center);
        assertThat(centers.findById(center.getId()).orElseThrow().getStatus()).isEqualTo(WelcomeCenterStatus.CLOSED);
        verify(lifecycle)
                .onWelcomeCenterClosed(
                        argThat(before -> before.status() == WelcomeCenterStatus.OPEN),
                        argThat(after -> after.status() == WelcomeCenterStatus.CLOSED));
        update(center);
        verify(lifecycle, times(1)).onWelcomeCenterClosed(any(), any());
        center.setStatus(WelcomeCenterStatus.OPEN);
        update(center);
        verify(lifecycle, times(1)).onWelcomeCenterOpened(any(), any());
        update(center);
        verify(lifecycle, times(1)).onWelcomeCenterOpened(any(), any());
    }

    @Test
    void failedSaveDoesNotCallHookOrChangeStatus() {
        WelcomeCenter first = create("N1FAIL");
        create("N1TAKEN");
        first.setCallsign("N1TAKEN");
        first.setStatus(WelcomeCenterStatus.CLOSED);
        client.put()
                .uri("/api/v1/welcome-centers/{id}", first.getId())
                .bodyValue(first)
                .exchange()
                .expectStatus()
                .is5xxServerError();
        assertThat(centers.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(WelcomeCenterStatus.OPEN);
        verify(lifecycle, never()).onWelcomeCenterClosed(any(), any());
    }

    @Test
    void editRequiresValidStatus() {
        WelcomeCenter center = create("N1VALID");
        for (Map<String, String> body :
                List.of(Map.of("callsign", "N1VALID"), Map.of("callsign", "N1VALID", "status", "BAD"))) {
            client.put()
                    .uri("/api/v1/welcome-centers/{id}", center.getId())
                    .bodyValue(body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }
        assertThat(centers.findById(center.getId()).orElseThrow().getStatus()).isEqualTo(WelcomeCenterStatus.OPEN);
    }

    @Test
    void tileStatusChangeChecksExpectedStateAndPreservesOtherFields() {
        WelcomeCenter center = create("N1TILE");
        center.setName("Keep this name");
        update(center);
        clearInvocations(lifecycle);
        client.patch()
                .uri("/api/v1/welcome-centers/{id}/status", center.getId())
                .bodyValue(Map.of("expectedStatus", "OPEN", "status", "CLOSED"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("CLOSED")
                .jsonPath("$.name")
                .isEqualTo("Keep this name");
        verify(lifecycle, times(1)).onWelcomeCenterClosed(any(), any());
        clearInvocations(lifecycle);
        client.patch()
                .uri("/api/v1/welcome-centers/{id}/status", center.getId())
                .bodyValue(Map.of("expectedStatus", "OPEN", "status", "CLOSED"))
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        verifyNoInteractions(lifecycle);
        assertThat(centers.findById(center.getId()).orElseThrow().getStatus()).isEqualTo(WelcomeCenterStatus.CLOSED);
        client.patch()
                .uri("/api/v1/welcome-centers/{id}/status", center.getId())
                .bodyValue(Map.of("expectedStatus", "CLOSED", "status", "OPEN"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("OPEN");
        verify(lifecycle, times(1)).onWelcomeCenterOpened(any(), any());
    }

    @Test
    void tileStatusChangeRejectsInvalidRequestsAndMissingCenters() {
        WelcomeCenter center = create("N1CHECK");
        for (var body : List.of(
                Map.of("status", "CLOSED"),
                Map.of("expectedStatus", "OPEN"),
                Map.of("expectedStatus", "BAD", "status", "CLOSED"))) {
            client.patch()
                    .uri("/api/v1/welcome-centers/{id}/status", center.getId())
                    .bodyValue(body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }
        client.patch()
                .uri("/api/v1/welcome-centers/{id}/status", java.util.UUID.randomUUID())
                .bodyValue(Map.of("expectedStatus", "OPEN", "status", "CLOSED"))
                .exchange()
                .expectStatus()
                .isNotFound();
        assertThat(centers.findById(center.getId()).orElseThrow().getStatus()).isEqualTo(WelcomeCenterStatus.OPEN);
    }
}
