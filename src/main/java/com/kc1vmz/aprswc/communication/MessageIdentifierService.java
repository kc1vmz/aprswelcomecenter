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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MessageIdentifierService {
    private final MessageDeliveryService deliveries;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public MessageIdentifierService(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this(jdbc, transactions, null);
    }

    @Autowired
    public MessageIdentifierService(
            JdbcTemplate jdbc, PlatformTransactionManager transactions, MessageDeliveryService deliveries) {
        this.deliveries = deliveries;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String next() {
        return transaction.execute(status -> {
            int number = jdbc.queryForObject(
                    "select next_value from message_identifier_counter where id=1 for update", Integer.class);
            for (int checked = 0; checked < 60466176; checked++) {
                String value = Integer.toString(number, 36).toUpperCase(Locale.ROOT);
                number = (number + 1) % 60466176;
                if (deliveries == null
                        || jdbc.queryForObject(
                                        "select count(*) from message_deliveries where identifier=? and state in ('PENDING','AWAITING_ACK')",
                                        Integer.class,
                                        value)
                                == 0) {
                    jdbc.update("update message_identifier_counter set next_value=? where id=1", number);
                    return value;
                }
            }
            throw new IllegalStateException("All message identifiers are in use");
        });
    }

    public void prepare(StationMessage message) {
        prepare(message, message.getOriginPolicyId(), message.getOriginPolicyVersion(), null, null);
    }

    public void prepare(StationMessage message, UUID policy, Long version, String occurrence, UUID claim) {
        String text = message.getContent();
        String to = message.getCallsignTo();
        if (message.getMessageType() != MessageType.MESSAGE
                || to == null
                || to.toUpperCase(Locale.ROOT).startsWith("BLN")
                || text == null
                || text.matches("(?:ack|rej)[A-Za-z0-9]{1,5}(?:}[A-Za-z0-9]{0,5})?")) return;
        if (message.getMessageIdentifier() == null) message.setMessageIdentifier(next());
        if (deliveries != null) deliveries.register(message, policy, version, occurrence, claim);
    }

    public static String wireContent(StationMessage message) {
        return message.getContent()
                + (message.getMessageIdentifier() == null ? "" : "{" + message.getMessageIdentifier());
    }
}
