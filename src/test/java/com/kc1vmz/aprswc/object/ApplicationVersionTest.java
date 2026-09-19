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
package com.kc1vmz.aprswc.object;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ApplicationVersionTest {
    @Test
    void constructorAndAccessorsExposeAllFields() {
        ApplicationVersion version =
                new ApplicationVersion("APRSWelcomeCenter", "1.0.3", "Author", "2026", "https://example.com");

        assertAll(
                () -> assertEquals("APRSWelcomeCenter", version.getApplicationName()),
                () -> assertEquals("1.0.3", version.getVersion()),
                () -> assertEquals("Author", version.getAuthor()),
                () -> assertEquals("2026", version.getCopyrightYear()),
                () -> assertEquals("https://example.com", version.getWebsite()));

        version.setApplicationName("APRSWC");
        version.setVersion("2.0");
        version.setAuthor("New Author");
        version.setCopyrightYear("2027");
        version.setWebsite("https://new.example.com");

        assertAll(
                () -> assertEquals("APRSWC", version.getApplicationName()),
                () -> assertEquals("2.0", version.getVersion()),
                () -> assertEquals("New Author", version.getAuthor()),
                () -> assertEquals("2027", version.getCopyrightYear()),
                () -> assertEquals("https://new.example.com", version.getWebsite()));
    }
}
