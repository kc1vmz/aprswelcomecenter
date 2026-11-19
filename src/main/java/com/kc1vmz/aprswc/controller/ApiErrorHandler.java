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

import com.kc1vmz.aprswc.exceptions.ApiFailure;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {
    public record FieldIssue(String field, String code, List<Object> parameters) {}

    public record ErrorBody(String code, List<Object> parameters, String message, List<FieldIssue> errors) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> failure(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String code =
                switch (status) {
                    case 400 -> "BAD_REQUEST";
                    case 404 -> "NOT_FOUND";
                    case 409 -> "CONFLICT";
                    default -> "REQUEST_FAILED";
                };
        List<Object> parameters = List.of(status);
        if (exception instanceof ApiFailure localized) {
            code = localized.getCode();
            parameters = localized.getParameters();
        }
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ErrorBody(
                        code,
                        parameters,
                        exception.getReason() == null ? "Request failed" : exception.getReason(),
                        List.of()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorBody> validation(WebExchangeBindException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody(
                        "VALIDATION_FAILED",
                        List.of(),
                        "Some fields contain invalid values.",
                        exception.getFieldErrors().stream()
                                .map(this::fieldIssue)
                                .toList()));
    }

    private FieldIssue fieldIssue(FieldError error) {
        String code =
                switch (error.getCode() == null ? "" : error.getCode()) {
                    case "NotNull", "NotBlank", "NotEmpty" -> "FIELD_REQUIRED";
                    case "Min", "DecimalMin" -> "FIELD_MIN";
                    case "Max", "DecimalMax" -> "FIELD_MAX";
                    case "Size" -> "FIELD_SIZE";
                    default -> "FIELD_INVALID";
                };
        List<Object> bounds = Arrays.stream(error.getArguments() == null ? new Object[0] : error.getArguments())
                .filter(value -> value instanceof Number)
                .toList();
        if (code.equals("FIELD_SIZE") && bounds.size() == 2) bounds = List.of(bounds.get(1), bounds.get(0));
        return new FieldIssue(error.getField(), code, bounds);
    }
}
