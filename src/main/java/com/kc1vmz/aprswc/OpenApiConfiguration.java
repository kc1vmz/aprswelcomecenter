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
package com.kc1vmz.aprswc;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI welcomeCenterOpenApi(@Value("${aprswc.application-version.version}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("APRS Welcome Center REST API")
                        .version(version)
                        .description(
                                "Manage Welcome Centers, stations, communication policies, connections, and application settings. "
                                        + "Try it out executes requests against this running instance; write requests change live data and may send APRS traffic.")
                        .license(new License()
                                .name("GNU GPL v3 or later")
                                .url("https://www.gnu.org/licenses/gpl-3.0.html")));
    }
}
