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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class InformationResponseTextTest {
    @Test
    void frenchInformationUsesSavedLanguageAndFitsRadioLimits() {
        var repository = mock(ApplicationSettingsRepository.class);
        var settings = new ApplicationSettings(null, null, ApplicationSettings.DEFAULT_TINY_TOPICS_SERVER_URL);
        settings.setInformationResponseLanguage("fr");
        when(repository.findAll()).thenReturn(List.of(settings));
        var text = new InformationResponseText(repository);
        assertThat(text.format("weather.unavailable")).isEqualTo("Aucun resume meteo disponible");
        assertThat(text.format("weather.summary", 72f, 40f, 1013f, 20f))
                .isEqualTo("Temp: 72F Hum: 40% Press: 1013Mb Lum: 20");
        assertThat(text.format("center.closed", "F1TEST")).isEqualTo("Centre APRS 'F1TEST' ferme.");
        var english = ResourceBundle.getBundle("information.messages", Locale.ROOT);
        var french = ResourceBundle.getBundle("information.messages", Locale.FRENCH);
        assertThat(french.getLocale()).isEqualTo(Locale.FRENCH);
        assertThat(french.keySet()).containsExactlyInAnyOrderElementsOf(english.keySet());
        for (String key : french.keySet()) {
            String value = key.startsWith("center.")
                    ? text.format(key, "KC1VMZ-15")
                    : key.equals("weather.summary") ? text.format(key, 72f, 40f, 1013f, 20f) : text.format(key);
            assertThat(value.length()).isLessThanOrEqualTo(67);
            assertThat(value.chars().allMatch(c -> c >= 32 && c <= 126)).isTrue();
            if (key.equals("center.open") || key.equals("center.daily"))
                assertThat(value).contains("HELP");
        }
        settings.setInformationResponseLanguage("en");
        assertThat(text.format("weather.unavailable")).isEqualTo("No current weather summary available");
    }

    @Test
    void spanishInformationUsesSavedLanguageAndRadioSafeText() {
        var repository = mock(ApplicationSettingsRepository.class);
        var settings = new ApplicationSettings(null, null, ApplicationSettings.DEFAULT_TINY_TOPICS_SERVER_URL);
        settings.setInformationResponseLanguage("es");
        when(repository.findAll()).thenReturn(List.of(settings));
        var text = new InformationResponseText(repository);
        assertThat(text.format("weather.unavailable")).isEqualTo("No hay resumen meteorologico disponible");
        assertThat(text.format("weather.summary", 72f, 40f, 1013f, 20f))
                .isEqualTo("Temp: 72F Hum: 40% Pres: 1013Mb Luz: 20");
        var english = ResourceBundle.getBundle("information.messages", Locale.ROOT);
        var spanish = ResourceBundle.getBundle("information.messages", Locale.forLanguageTag("es"));
        assertThat(spanish.keySet()).containsExactlyInAnyOrderElementsOf(english.keySet());
        for (String key : spanish.keySet()) {
            String value = key.startsWith("center.")
                    ? text.format(key, "KC1VMZ-15")
                    : key.equals("weather.summary") ? text.format(key, 72f, 40f, 1013f, 20f) : text.format(key);
            assertThat(value.length()).isLessThanOrEqualTo(67);
            assertThat(value.chars().allMatch(c -> c >= 32 && c <= 126)).isTrue();
            if (key.equals("center.open") || key.equals("center.daily"))
                assertThat(value).contains("HELP");
        }
        settings.setInformationResponseLanguage("en");
        assertThat(text.format("weather.unavailable")).isEqualTo("No current weather summary available");
    }

    @Test
    void defaultsToEnglishAndReadsChangedPreferenceForEachResponse() {
        var repository = mock(ApplicationSettingsRepository.class);
        var text = new InformationResponseText(repository);
        assertThat(text.format("weather.unavailable")).isEqualTo("No current weather summary available");
        var settings = new ApplicationSettings(null, null, ApplicationSettings.DEFAULT_TINY_TOPICS_SERVER_URL);
        when(repository.findAll()).thenReturn(List.of(settings));
        settings.setInformationResponseLanguage("xx");
        assertThat(text.format("weather.summary", 72f, 40f, 1013f, 20f))
                .isEqualTo("xxTemp: 72F Hum: 40% Bar: 1013Mb Lum: 20xx");
        assertThat(text.format("center.closed", "K4BO")).isEqualTo("xxAPRS Welcome Center 'K4BO' now closed.xx");
        settings.setInformationResponseLanguage("en");
        assertThat(text.format("center.closed", "K4BO")).isEqualTo("APRS Welcome Center 'K4BO' now closed.");
    }

    @Test
    void pseudoCatalogWrapsEveryEnglishPhraseAndBulletinsFitLongCallsigns() {
        var english = ResourceBundle.getBundle("information.messages", Locale.ROOT);
        var pseudo = ResourceBundle.getBundle("information.messages", Locale.forLanguageTag("xx"));
        for (String key : english.keySet()) {
            assertThat(pseudo.getString(key)).isEqualTo("xx" + english.getString(key) + "xx");
            if (key.startsWith("center.")) {
                assertThat(String.format(pseudo.getString(key), "KC1VMZ-15").length())
                        .isLessThanOrEqualTo(67);
            }
        }
    }
}
