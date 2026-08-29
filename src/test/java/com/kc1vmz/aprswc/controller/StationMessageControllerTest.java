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

import com.kc1vmz.aprswc.database.StationMessageRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class StationMessageControllerTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private StationMessageRepository repository;

    @Autowired
    private WelcomeCenterRepository welcomeCenters;

    @BeforeEach
    void clearMessages() {
        repository.deleteAll();
    }

    @AfterEach
    void removeMessages() {
        repository.deleteAll();
    }

    @Test
    void filtersMessagesByDestinationCallsign() {
        repository.save(new StationMessage(
                null,
                "N1ABC",
                "KC1VMZ",
                null,
                null,
                "included",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));
        repository.save(new StationMessage(
                null,
                "N2OTHER",
                "KC1VMZ",
                null,
                null,
                "excluded",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));

        client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/station-messages")
                        .queryParam("callsignTo", "n1abc")
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].content")
                .isEqualTo("included")
                .jsonPath("$[1]")
                .doesNotExist();
    }

    @Test
    void createsOutgoingMessage() {
        StationMessage message = new StationMessage(
                null, "N1ABC", "KC1VMZ", null, null, "Hello", null, com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);

        client.post()
                .uri("/api/v1/station-messages")
                .bodyValue(message)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.callsignTo")
                .isEqualTo("N1ABC")
                .jsonPath("$.callsignFrom")
                .isEqualTo("KC1VMZ")
                .jsonPath("$.content")
                .isEqualTo("Hello");
    }

    @Test
    void filtersAndCreatesMessagesForWelcomeCenter() {
        WelcomeCenter center = welcomeCenters.save(new WelcomeCenter(
                null,
                "Message Center",
                null,
                "MSGCTR",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "c",
                "/",
                List.of()));
        repository.save(new StationMessage(
                null,
                null,
                "KC1VMZ",
                center,
                null,
                "center message",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));
        repository.save(new StationMessage(
                null,
                "N1ABC",
                "KC1VMZ",
                null,
                null,
                "station message",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));

        client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/station-messages")
                        .queryParam("welcomeCenterId", center.getId())
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].content")
                .isEqualTo("center message")
                .jsonPath("$[1]")
                .doesNotExist();

        StationMessage outgoing = new StationMessage(
                null,
                null,
                "KC1VMZ",
                center,
                null,
                "new message",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        client.post()
                .uri("/api/v1/station-messages")
                .bodyValue(outgoing)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.welcomeCenter.id")
                .isEqualTo(center.getId().toString())
                .jsonPath("$.callsignTo")
                .doesNotExist();
    }

    @Test
    void deletesMessagesForDestinationCallsignOnly() {
        repository.save(new StationMessage(
                null,
                "N1ABC",
                "KC1VMZ",
                null,
                null,
                "delete",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));
        repository.save(new StationMessage(
                null, "N2KEEP", "KC1VMZ", null, null, "keep", null, com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));

        client.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/station-messages")
                        .queryParam("callsignTo", "n1abc")
                        .build())
                .exchange()
                .expectStatus()
                .isNoContent();

        org.assertj.core.api.Assertions.assertThat(repository.findAll())
                .extracting(StationMessage::getCallsignTo)
                .containsExactly("N2KEEP");
    }

    @Test
    void deletesMessagesForWelcomeCenterOnly() {
        WelcomeCenter deletedCenter = welcomeCenters.save(new WelcomeCenter(
                null,
                "Delete Center",
                null,
                "DELCTR",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "c",
                "/",
                List.of()));
        WelcomeCenter retainedCenter = welcomeCenters.save(new WelcomeCenter(
                null,
                "Keep Center",
                null,
                "KEEPCTR",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "c",
                "/",
                List.of()));
        repository.save(new StationMessage(
                null,
                null,
                "KC1VMZ",
                deletedCenter,
                null,
                "delete",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));
        repository.save(new StationMessage(
                null,
                null,
                "KC1VMZ",
                retainedCenter,
                null,
                "keep",
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE));

        client.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/station-messages")
                        .queryParam("welcomeCenterId", deletedCenter.getId())
                        .build())
                .exchange()
                .expectStatus()
                .isNoContent();

        org.assertj.core.api.Assertions.assertThat(repository.findAll())
                .extracting(message -> message.getWelcomeCenter().getId())
                .containsExactly(retainedCenter.getId());
    }

    @Test
    void rejectsUnscopedBulkDelete() {
        client.delete()
                .uri("/api/v1/station-messages")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
