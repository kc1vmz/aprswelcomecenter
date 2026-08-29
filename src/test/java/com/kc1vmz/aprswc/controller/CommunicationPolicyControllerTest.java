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

import com.kc1vmz.aprswc.database.CommunicationCategoryRepository;
import com.kc1vmz.aprswc.database.CommunicationPolicyRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.CommunicationCategory;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
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
class CommunicationPolicyControllerTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private CommunicationPolicyRepository policies;

    @Autowired
    private CommunicationCategoryRepository categories;

    @Autowired
    private WelcomeCenterRepository welcomeCenters;

    @BeforeEach
    @AfterEach
    void clearData() {
        policies.deleteAll();
        categories.deleteAll();
        welcomeCenters.deleteAll();
    }

    @Test
    void filtersPoliciesByWelcomeCenter() {
        CommunicationCategory category =
                categories.save(new CommunicationCategory(null, "General", "General messages", false, false));
        WelcomeCenter firstCenter = welcomeCenters.save(new WelcomeCenter(
                null, "First", null, "N1ABC", null, null, null, null, null, null, null, null, "c", "/", List.of()));
        WelcomeCenter secondCenter = welcomeCenters.save(new WelcomeCenter(
                null, "Second", null, "N2ABC", null, null, null, null, null, null, null, null, "c", "/", List.of()));
        policies.save(new CommunicationPolicy(
                null,
                category,
                firstCenter,
                "First message",
                null,
                null,
                MessageType.MESSAGE,
                CommunicationEventType.ENTER_REGION));
        policies.save(new CommunicationPolicy(
                null,
                category,
                secondCenter,
                "Second message",
                null,
                null,
                MessageType.BULLETIN,
                CommunicationEventType.EXIT_REGION));

        client.get()
                .uri("/api/v1/communication-policies?welcomeCenterId={id}", firstCenter.getId())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].messageText")
                .isEqualTo("First message")
                .jsonPath("$[0].welcomeCenter.id")
                .isEqualTo(firstCenter.getId().toString())
                .jsonPath("$[1]")
                .doesNotExist();

        client.get()
                .uri("/api/v1/communication-policies")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(CommunicationPolicy.class)
                .hasSize(2);
    }
}
