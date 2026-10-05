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

import com.kc1vmz.aprswc.database.CommunicationInstanceRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:communication_api;DB_CLOSE_DELAY=-1")
@AutoConfigureWebTestClient
class CommunicationInstanceControllerTest {
    @Autowired
    WebTestClient client;

    @Autowired
    CommunicationInstanceRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void creationRequiresAllApplicableFieldsExceptInitializationCommands() {
        for (String type :
                new String[] {"APRS_IS", "KISS_TCP", "TNC2_TCP", "KISS_SERIAL", "TNC2_SERIAL", "KENWOOD_SERIAL"}) {
            var body = new HashMap<String, Object>();
            body.put("type", type);
            body.put("state", "PAUSED");
            if (type.endsWith("SERIAL")) {
                body.put("serialDevice", "missing-validation-test-port");
                body.put("baudRate", 9600);
            } else {
                body.put("host", "localhost");
                body.put("port", 8001);
            }
            if (type.equals("APRS_IS") || type.equals("KENWOOD_SERIAL")) body.put("username", "N1TEST");
            if (type.equals("APRS_IS")) {
                body.put("passcode", "12345");
                body.put("filter", "m/50");
            } else body.put("digiPath", "WIDE1-1");
            for (String field : body.keySet()) {
                var invalid = new HashMap<>(body);
                invalid.put(field, field.equals("port") || field.equals("baudRate") ? null : "   ");
                client.post()
                        .uri("/api/v1/communication-instances")
                        .bodyValue(invalid)
                        .exchange()
                        .expectStatus()
                        .isBadRequest();
            }
            client.post()
                    .uri("/api/v1/communication-instances")
                    .bodyValue(body)
                    .exchange()
                    .expectStatus()
                    .isOk();
        }
    }

    @Test
    void kenwoodPersistsMycallAndRejectsPortConflicts() {
        var body = new HashMap<String, Object>(Map.of(
                "type",
                "KENWOOD_SERIAL",
                "state",
                "PAUSED",
                "serialDevice",
                "missing-kenwood-test-port",
                "baudRate",
                9600,
                "username",
                "n1abc-10",
                "digiPath",
                "wide1-1",
                "transmitEnabled",
                false));
        var created = client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(created.get("username")).isEqualTo("N1ABC-10");
        assertThat(created.get("label").toString()).contains("Kenwood Serial", "N1ABC-10");
        var saved = repository
                .findById(UUID.fromString(created.get("id").toString()))
                .orElseThrow();
        assertThat(created).doesNotContainKey("transmitEnabled");
        assertThat(saved.getDigiPath()).isEqualTo("WIDE1-1");
        client.get()
                .uri("/api/v1/communication-instances/serialPorts")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.ports")
                .isArray();
        body.put("state", "ACTIVE");
        client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk();
        for (String type : List.of("KENWOOD_SERIAL", "KISS_SERIAL", "TNC2_SERIAL")) {
            body.put("type", type);
            client.post()
                    .uri("/api/v1/communication-instances")
                    .bodyValue(body)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(409);
        }
        body.put("type", "KENWOOD_SERIAL");
        body.put("serialDevice", "another-missing-kenwood-port");
        client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk();
        body.put("state", "PAUSED");
        body.put("username", "TOOLONGCALL");
        client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isBadRequest();
        body.put("username", "N1ABC");
        body.put("initCommand1", "RESET");
        client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void crudProtectsSecretsVersionsAndPreservesPasscode() {
        var body = new HashMap<String, Object>(Map.of(
                "type",
                "APRS_IS",
                "state",
                "PAUSED",
                "host",
                "127.0.0.1",
                "port",
                14580,
                "username",
                "N1TEST",
                "passcode",
                "12345",
                "filter",
                "m/25"));
        var created = client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(created).doesNotContainKey("passcode");
        String id = created.get("id").toString();
        body.put("version", created.get("version"));
        body.remove("passcode");
        body.put("filter", "m/50");
        client.put()
                .uri("/api/v1/communication-instances/" + id)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(repository.findById(UUID.fromString(id)).orElseThrow().getPasscode())
                .isEqualTo("12345");
        client.put()
                .uri("/api/v1/communication-instances/" + id)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        client.delete()
                .uri("/api/v1/communication-instances/" + id + "?version=0")
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        client.get()
                .uri("/api/v1/communication-instances")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].configuration.passcode")
                .doesNotExist()
                .jsonPath("$[0].health.status")
                .isEqualTo("STOPPED");
        long version = repository.findById(UUID.fromString(id)).orElseThrow().getVersion();
        client.delete()
                .uri("/api/v1/communication-instances/" + id + "?version=" + version)
                .exchange()
                .expectStatus()
                .isNoContent();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void validatesTypesAndAllowsPausedSerialDuplicatesButRejectsActiveConflict() {
        var body = new HashMap<String, Object>(Map.of(
                "type",
                "KISS_SERIAL",
                "state",
                "PAUSED",
                "serialDevice",
                "nonexistent-test-device",
                "baudRate",
                9600,
                "digiPath",
                "WIDE1-1"));
        for (int i = 0; i < 2; i++)
            client.post()
                    .uri("/api/v1/communication-instances")
                    .bodyValue(body)
                    .exchange()
                    .expectStatus()
                    .isOk();
        body.put("state", "ACTIVE");
        client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk();
        client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isEqualTo(409);
        body.put("type", "INVALID");
        client.post()
                .uri("/api/v1/communication-instances")
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
