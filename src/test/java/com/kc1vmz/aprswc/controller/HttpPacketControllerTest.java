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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.kc1vmz.aprswc.object.StationPacket;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HttpPacketControllerTest {
    @Autowired
    private WebTestClient client;

    @Autowired
    private HttpPacketController controller;

    @Test
    void initializesStablePacketProcessorIdAtStartup() {
        String initializedId = (String) ReflectionTestUtils.getField(controller, "packetProcessorId");

        assertAll(() -> assertNotNull(initializedId), () -> assertEquals("HTTP", initializedId));
    }

    @Test
    void acceptsPacketForQueueProcessing() {
        StationPacket packet = new StationPacket(null, "http", "KC1VMZ", LocalDateTime.now(), "packet body", null);

        client.post()
                .uri("/api/v1/http-packets")
                .bodyValue(packet)
                .exchange()
                .expectStatus()
                .isAccepted()
                .expectBody()
                .isEmpty();
    }

    @Test
    void doesNotExposeGetMethod() {
        client.get().uri("/api/v1/http-packets").exchange().expectStatus().isEqualTo(405);
    }
}
