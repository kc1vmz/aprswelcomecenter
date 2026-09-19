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
package com.kc1vmz.aprswc.accessor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kc1vmz.aprswc.object.ApplicationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationVersionAccessorTest {
    @Test
    void findReturnsConfiguredApplicationVersion() {
        ApplicationVersionAccessor accessor = new ApplicationVersionAccessor();
        ReflectionTestUtils.setField(accessor, "applicationName", "APRSWelcomeCenter");
        ReflectionTestUtils.setField(accessor, "version", "1.0.3");
        ReflectionTestUtils.setField(accessor, "author", "Author");
        ReflectionTestUtils.setField(accessor, "copyrightYear", "2026");
        ReflectionTestUtils.setField(accessor, "website", "https://example.com");

        ApplicationVersion version = accessor.find().block();

        assertAll(
                () -> assertEquals("APRSWelcomeCenter", version.getApplicationName()),
                () -> assertEquals("1.0.3", version.getVersion()),
                () -> assertEquals("Author", version.getAuthor()),
                () -> assertEquals("2026", version.getCopyrightYear()),
                () -> assertEquals("https://example.com", version.getWebsite()));
    }
}
