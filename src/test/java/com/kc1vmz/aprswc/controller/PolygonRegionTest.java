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

import static org.assertj.core.api.Assertions.assertThat;

import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.database.WelcomeRegionRepository;
import com.kc1vmz.aprswc.enumeration.RegionType;
import com.kc1vmz.aprswc.object.RegionVertex;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class PolygonRegionTest {
    @Autowired
    WebTestClient client;

    @Autowired
    WelcomeCenterRepository centers;

    @Autowired
    WelcomeRegionRepository regions;

    @Autowired
    JdbcTemplate jdbc;

    private WelcomeRegion polygon() {
        var region = new WelcomeRegion(
                null, "Polygon", null, RegionType.POLYGON, null, null, null, null, null, null, null, null);
        region.setVertices(List.of(
                new RegionVertex(42.123456789, -72.0), new RegionVertex(42.0, -71.0), new RegionVertex(43.0, -71.0)));
        return region;
    }

    @Test
    void roundTripValidationReplacementTypeChangeAndDeletion() {
        var center = centers.saveAndFlush(new WelcomeCenter(
                null,
                "Polygon center",
                null,
                "N1POLY",
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
        String url = "/api/v1/welcome-centers/" + center.getId() + "/regions";
        try {
            var region = client.post()
                    .uri(url)
                    .bodyValue(polygon())
                    .exchange()
                    .expectStatus()
                    .isCreated()
                    .expectBody(WelcomeRegion.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(region).isNotNull();
            var id = region.getId();
            var reloaded = regions.findById(id).orElseThrow();
            assertThat(reloaded.getVertices())
                    .extracting(RegionVertex::getLatitude)
                    .containsExactly(42.123456789, 42.0, 43.0);
            var invalid = polygon();
            invalid.setVertices(List.of(new RegionVertex(0.0, 0.0)));
            client.put()
                    .uri(url + "/" + id)
                    .bodyValue(invalid)
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
            assertThat(regions.findById(id).orElseThrow().getVertices()).hasSize(3);
            var replacement = polygon();
            replacement.setVertices(List.of(
                    new RegionVertex(40.0, -72.0),
                    new RegionVertex(40.0, -71.0),
                    new RegionVertex(41.0, -71.0),
                    new RegionVertex(41.0, -72.0)));
            client.put()
                    .uri(url + "/" + id)
                    .bodyValue(replacement)
                    .exchange()
                    .expectStatus()
                    .isOk();
            assertThat(regions.findById(id).orElseThrow().getVertices()).hasSize(4);
            replacement.setType(RegionType.RECTANGLE);
            client.put()
                    .uri(url + "/" + id)
                    .bodyValue(replacement)
                    .exchange()
                    .expectStatus()
                    .isOk();
            assertThat(jdbc.queryForObject(
                            "select count(*) from welcome_region_vertices where region_id=?", Integer.class, id))
                    .isZero();
            client.put()
                    .uri(url + "/" + id)
                    .bodyValue(polygon())
                    .exchange()
                    .expectStatus()
                    .isOk();
            client.delete().uri(url + "/" + id).exchange().expectStatus().isNoContent();
            assertThat(jdbc.queryForObject(
                            "select count(*) from welcome_region_vertices where region_id=?", Integer.class, id))
                    .isZero();
            client.post()
                    .uri(url)
                    .bodyValue(polygon())
                    .exchange()
                    .expectStatus()
                    .isCreated();
        } finally {
            centers.deleteById(center.getId());
        }
        assertThat(jdbc.queryForObject(
                        "select count(*) from welcome_region_vertices v left join welcome_regions r on r.id=v.region_id where r.id is null",
                        Integer.class))
                .isZero();
    }
}
