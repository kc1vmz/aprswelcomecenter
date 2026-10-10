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

import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.StationMessage;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MessageIdentifierService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public MessageIdentifierService(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String next() {
        return transaction.execute(status -> {
            int number = jdbc.queryForObject(
                    "select next_value from message_identifier_counter where id=1 for update", Integer.class);
            jdbc.update("update message_identifier_counter set next_value=? where id=1", (number + 1) % 60466176);
            String value = Integer.toString(number, 36).toUpperCase(Locale.ROOT);
            return "0".repeat(5 - value.length()) + value;
        });
    }

    public void prepare(StationMessage message) {
        String text = message.getContent();
        String to = message.getCallsignTo();
        if (message.getMessageType() != MessageType.MESSAGE
                || to == null
                || to.toUpperCase(Locale.ROOT).startsWith("BLN")
                || text == null
                || text.matches("(?:ack|rej)[A-Za-z0-9]{1,5}")) return;
        if (message.getMessageIdentifier() == null) message.setMessageIdentifier(next());
    }

    public static String wireContent(StationMessage message) {
        return message.getContent()
                + (message.getMessageIdentifier() == null ? "" : "{" + message.getMessageIdentifier());
    }
}
