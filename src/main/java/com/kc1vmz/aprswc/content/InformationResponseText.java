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
package com.kc1vmz.aprswc.content;

import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import java.util.Locale;
import java.util.ResourceBundle;
import org.springframework.stereotype.Service;

/** Server-owned information language, independent of browser/request locales. */
@Service
public class InformationResponseText {
    private final ApplicationSettingsRepository settings;

    public InformationResponseText(ApplicationSettingsRepository settings) {
        this.settings = settings;
    }

    public String format(String key, Object... arguments) {
        String language = settings.findAll().stream()
                .findFirst()
                .map(value -> value.getInformationResponseLanguage())
                .orElse("en");
        Locale locale = Locale.forLanguageTag(
                switch (language) {
                    case "xx", "es", "fr" -> language;
                    default -> "en";
                });
        ResourceBundle catalog = ResourceBundle.getBundle(
                "information.messages",
                locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        return String.format(Locale.ROOT, catalog.getString(key), arguments);
    }
}
