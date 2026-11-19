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

import com.kc1vmz.aprswc.exceptions.ApiFailure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class ApiErrorHandlerTest {
    @RestController
    static class ExampleController {
        record Input(@NotBlank String name, @Min(1) int days) {}

        @GetMapping("/stale")
        String stale() {
            throw new ApiFailure(HttpStatus.CONFLICT, "POLICY_CHANGED", "Diagnostic wording may change");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Input input) {}
    }

    @Test
    void staleCodeDoesNotDependOnDiagnosticWording() {
        WebTestClient.bindToController(new ExampleController())
                .controllerAdvice(new ApiErrorHandler())
                .build()
                .get()
                .uri("/stale")
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("POLICY_CHANGED")
                .jsonPath("$.message")
                .isEqualTo("Diagnostic wording may change")
                .jsonPath("$.parameters")
                .isArray();
    }

    @Test
    void beanValidationReturnsFieldCodesAndConstraintParameters() {
        WebTestClient.bindToController(new ExampleController())
                .controllerAdvice(new ApiErrorHandler())
                .build()
                .post()
                .uri("/validate")
                .bodyValue(new ExampleController.Input("", 0))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[?(@.field == 'name')].code")
                .isEqualTo("FIELD_REQUIRED")
                .jsonPath("$.errors[?(@.field == 'days')].code")
                .isEqualTo("FIELD_MIN");
    }

    @Test
    void unknownErrorsHaveLocalizedGenericIdentityAndKnownErrorsKeepParameters() {
        var handler = new ApiErrorHandler();
        assertThat(handler.failure(new ResponseStatusException(HttpStatus.NOT_FOUND))
                        .getBody()
                        .code())
                .isEqualTo("NOT_FOUND");
        var body = handler.failure(new ApiFailure(HttpStatus.BAD_REQUEST, "MESSAGE_LENGTH", "Diagnostic", 67))
                .getBody();
        assertThat(body.parameters()).containsExactly(67);
    }
}
