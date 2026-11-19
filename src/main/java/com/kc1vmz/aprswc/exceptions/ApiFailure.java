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
package com.kc1vmz.aprswc.exceptions;

import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/** A stable UI error identity; the diagnostic reason is not used for behavior. */
public class ApiFailure extends ResponseStatusException {
    private final String code;
    private final List<Object> parameters;

    public ApiFailure(HttpStatusCode status, String code, String reason, Object... parameters) {
        super(status, reason);
        this.code = code;
        this.parameters = List.of(parameters);
    }

    public String getCode() {
        return code;
    }

    public List<Object> getParameters() {
        return parameters;
    }
}
