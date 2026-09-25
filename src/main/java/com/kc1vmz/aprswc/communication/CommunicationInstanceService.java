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
package com.kc1vmz.aprswc.communication;

import com.kc1vmz.aprswc.database.CommunicationInstanceRepository;
import com.kc1vmz.aprswc.object.CommunicationInstance;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunicationInstanceService {
    private final CommunicationInstanceRepository repository;
    private final CommunicationInstanceManager manager;
    private final TransactionTemplate transaction;

    @Autowired
    private CenterCommunicationRouting routing;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher events;

    public CommunicationInstanceService(
            CommunicationInstanceRepository repository,
            CommunicationInstanceManager manager,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.manager = manager;
        transaction = new TransactionTemplate(transactions);
    }

    public record View(
            CommunicationInstance configuration, CommunicationInstanceManager.Health health, List<String> warnings) {}

    public List<View> list() {
        var all = repository.findAll();
        return all.stream()
                .map(c -> new View(c, manager.health(c.getId()), warnings(c, all)))
                .toList();
    }

    private List<String> warnings(CommunicationInstance c, List<CommunicationInstance> all) {
        if (!"KISS_SERIAL".equals(c.getType())
                && all.stream()
                        .anyMatch(other -> !other.getId().equals(c.getId())
                                && Objects.equals(other.getHost(), c.getHost())
                                && Objects.equals(other.getPort(), c.getPort())))
            return List.of("Another instance uses the same TCP endpoint.");
        return List.of();
    }

    public synchronized CommunicationInstance save(UUID id, CommunicationInstance value) {
        var saved = transaction.execute(status -> {
            routing.lockConfiguration();
            if (id == null) {
                value.setId(UUID.randomUUID());
                value.setVersion(0);
            } else {
                var old = repository
                        .findById(id)
                        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Communication instance no longer exists"));
                if (value.getVersion() != old.getVersion())
                    throw error(HttpStatus.CONFLICT, "Configuration changed. Refresh and try again.");
                value.setId(id);
                if (value.getPasscode() == null || value.getPasscode().isBlank()) value.setPasscode(old.getPasscode());
            }
            validate(value);
            if ("ACTIVE".equals(value.getState())
                    && "KISS_SERIAL".equals(value.getType())
                    && repository.findAll().stream()
                            .anyMatch(c -> !c.getId().equals(value.getId())
                                    && "ACTIVE".equals(c.getState())
                                    && "KISS_SERIAL".equals(c.getType())
                                    && value.getSerialDevice().equalsIgnoreCase(c.getSerialDevice())))
                throw error(HttpStatus.CONFLICT, "Another ACTIVE instance already owns this serial device.");
            return repository.saveAndFlush(value);
        });
        manager.reconcile(); // TransactionTemplate has committed before the runtime changes.
        if ("ACTIVE".equals(saved.getState()))
            events.publishEvent(new CenterCommunicationRouting.InstanceAvailable(saved.getId()));
        return saved;
    }

    public synchronized void delete(UUID id, long expectedVersion) {
        transaction.executeWithoutResult(status -> {
            routing.lockConfiguration();
            var value = repository
                    .findById(id)
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Communication instance no longer exists"));
            if (value.getVersion() != expectedVersion)
                throw error(HttpStatus.CONFLICT, "Configuration changed. Refresh and try again.");
            routing.beforeInstanceDeleted(id);
            repository.delete(value);
            repository.flush();
        });
        manager.reconcile();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private static void required(String value, String field) {
        if (value == null || value.isBlank()) throw error(HttpStatus.BAD_REQUEST, field + " is required");
    }

    private static void validate(CommunicationInstance c) {
        if (!Set.of("APRS_IS", "KISS_TCP", "KISS_SERIAL").contains(Objects.toString(c.getType(), "")))
            throw error(HttpStatus.BAD_REQUEST, "Invalid communication type");
        if (!Set.of("ACTIVE", "PAUSED").contains(Objects.toString(c.getState(), "")))
            throw error(HttpStatus.BAD_REQUEST, "Invalid state");
        for (String s : new String[] {
            c.getHost(),
            c.getUsername(),
            c.getPasscode(),
            c.getFilter(),
            c.getSerialDevice(),
            c.getDigiPath(),
            c.getInitCommand1(),
            c.getInitCommand2()
        })
            if (s != null && (s.length() > 255 || s.chars().anyMatch(ch -> ch < 32 || ch > 126)))
                throw error(
                        HttpStatus.BAD_REQUEST,
                        "Configuration fields must contain at most 255 printable ASCII characters");
        if ("KISS_SERIAL".equals(c.getType())) {
            required(c.getSerialDevice(), "Serial device");
            c.setSerialDevice(c.getSerialDevice().trim());
            if (c.getBaudRate() == null || c.getBaudRate() < 1 || c.getBaudRate() > 4000000)
                throw error(HttpStatus.BAD_REQUEST, "Invalid baud rate");
            c.setHost(null);
            c.setPort(null);
        } else {
            required(c.getHost(), "Host");
            c.setHost(c.getHost().trim());
            if (c.getPort() == null || c.getPort() < 1 || c.getPort() > 65535)
                throw error(HttpStatus.BAD_REQUEST, "Invalid TCP port");
            c.setSerialDevice(null);
            c.setBaudRate(null);
            c.setInitCommand1(null);
            c.setInitCommand2(null);
        }
        if ("APRS_IS".equals(c.getType())) {
            required(c.getUsername(), "Username");
            required(c.getPasscode(), "Passcode");
            if (!c.getUsername().matches("[A-Za-z0-9-]{1,10}")
                    || !c.getPasscode().matches("-?[0-9]+"))
                throw error(HttpStatus.BAD_REQUEST, "Invalid APRS-IS username or passcode");
            c.setDigiPath(null);
        } else {
            c.setUsername(null);
            c.setPasscode(null);
            c.setFilter(null);
        }
    }
}
