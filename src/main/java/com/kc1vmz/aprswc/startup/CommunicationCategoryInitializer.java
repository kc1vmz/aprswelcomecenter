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
package com.kc1vmz.aprswc.startup;

import com.kc1vmz.aprswc.accessor.CommunicationCategoryAccessor;
import com.kc1vmz.aprswc.object.CommunicationCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CommunicationCategoryInitializer implements ApplicationRunner {
    private static final List<CommunicationCategory> DEFAULT_CATEGORIES = List.of(
            new CommunicationCategory(
                    UUID.fromString("10000000-0000-0000-0000-000000000001"),
                    "Welcome",
                    "Send a welcome message upon entering the welcome center's region.",
                    false,
                    true),
            new CommunicationCategory(
                    UUID.fromString("10000000-0000-0000-0000-000000000002"),
                    "Goodbye",
                    "Send a goodbye message upon leaving the welcome center's region.",
                    false,
                    true),
            new CommunicationCategory(
                    UUID.fromString("10000000-0000-0000-0000-000000000003"),
                    "Weather",
                    "Send a weather report based on local APRS weather",
                    true,
                    true),
            new CommunicationCategory(
                    UUID.fromString("10000000-0000-0000-0000-000000000004"),
                    "Commercial Radio",
                    "Send information about commercial radio in the area",
                    false,
                    true),
            new CommunicationCategory(
                    UUID.fromString("10000000-0000-0000-0000-000000000005"),
                    "Events",
                    "Send information about current local events",
                    false,
                    true),
            new CommunicationCategory(
                    UUID.fromString("10000000-0000-0000-0000-000000000006"),
                    "Clubs",
                    "Send information about local amateur clubs",
                    false,
                    true),
            new CommunicationCategory(
                    UUID.fromString("10000000-0000-0000-0000-000000000007"),
                    "Amateur Radio",
                    "Send information about amateur radio infrastructure in the area",
                    false,
                    true));

    @Autowired
    private CommunicationCategoryAccessor accessor;

    @Override
    public void run(ApplicationArguments arguments) {
        DEFAULT_CATEGORIES.forEach(category -> accessor.createIfAbsent(category).block());
    }
}
