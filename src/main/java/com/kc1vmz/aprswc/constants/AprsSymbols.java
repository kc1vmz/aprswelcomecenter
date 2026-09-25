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
package com.kc1vmz.aprswc.constants;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The same bundled catalog is used by the text chooser and API validation. */
public final class AprsSymbols {
    private static final Set<String> SYMBOLS = load();

    private AprsSymbols() {}

    private static Set<String> load() {
        try (var stream = AprsSymbols.class.getResourceAsStream("/static/aprs-symbols.json")) {
            if (stream == null) throw new IOException("Missing APRS symbol catalog");
            Set<String> symbols = new HashSet<>();
            for (var symbol : new ObjectMapper().readTree(stream).get("symbols"))
                symbols.add(symbol.get("table").asText() + symbol.get("code").asText());
            return Set.copyOf(symbols);
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    public static void validate(String table, String code, String previousTable, String previousCode) {
        // Keep existing custom/overlay values without allowing new arbitrary combinations.
        if (table != null && code != null && Objects.equals(table, previousTable) && Objects.equals(code, previousCode))
            return;
        if (table == null
                || table.length() != 1
                || code == null
                || code.length() != 1
                || !SYMBOLS.contains(table + code))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose a named APRS symbol from the Primary or Alternate table");
    }
}
