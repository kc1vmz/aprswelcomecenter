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
package com.kc1vmz.aprswc.importregion;

import static org.assertj.core.api.Assertions.*;

import com.kc1vmz.aprswc.controller.RegionImportController;
import com.kc1vmz.aprswc.importregion.RegionImportService.ImportSummary;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

class RegionImportControllerTest {
    @Test
    void streamedFolderUploadThenReadAndDelete() throws Exception {
        var service = new RegionImportService();
        var client = WebTestClient.bindToController(new RegionImportController(service))
                .build()
                .mutate()
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        var body = new MultipartBodyBuilder();
        for (var entry : ShapefileReaderTest.acton().entrySet())
            body.part("files", entry.getValue()).filename("towns/acton." + entry.getKey());
        var summary = client.post()
                .uri("/api/v1/region-imports")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ImportSummary.class)
                .returnResult()
                .getResponseBody();
        assertThat(summary).isNotNull();
        assertThat(summary.layers().get(0).name()).isEqualTo("towns/acton");
        String url = "/api/v1/region-imports/" + summary.id();
        client.get()
                .uri(url + "/layers/0/features?query=Acton&label=TOWN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].name")
                .isEqualTo("ACTON");
        client.get()
                .uri(url + "/layers/0/features/0")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.rings[0].length()")
                .isEqualTo(12)
                .jsonPath("$.problem")
                .isEmpty();
        client.delete().uri(url).exchange().expectStatus().isNoContent();
        client.get()
                .uri(url + "/layers/0/features/0")
                .exchange()
                .expectStatus()
                .isEqualTo(410)
                .expectBody()
                .jsonPath("$.message")
                .value(value -> assertThat(value.toString()).contains("expired"));
    }
}
