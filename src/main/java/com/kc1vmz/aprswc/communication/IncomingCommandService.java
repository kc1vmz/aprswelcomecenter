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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Durable, atomic suppression of repeated numbered commands for 30 minutes. */
@Service
public class IncomingCommandService {
    private final JdbcTemplate jdbc;

    public IncomingCommandService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(UUID center, String sender, String text) {
        String identifier = AprsMessageProtocol.identifier(text);
        if (identifier == null) return true;
        Instant now = Instant.now();
        jdbc.update("delete from incoming_command_receipts where expires_at <= ?", Timestamp.from(now));
        try {
            jdbc.update(
                    "insert into incoming_command_receipts(center_id,sender,identifier,command_text,expires_at) values(?,?,?,?,?)",
                    center,
                    sender.toUpperCase(Locale.ROOT),
                    identifier,
                    AprsMessageProtocol.body(text),
                    Timestamp.from(now.plusSeconds(1800)));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}
