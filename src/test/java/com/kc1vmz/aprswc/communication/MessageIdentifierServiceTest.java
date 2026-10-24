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

import static org.assertj.core.api.Assertions.assertThat;

import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.StationMessage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MessageIdentifierServiceTest {
    @Test
    void persistentAtomicBase36SequenceAndProtocolExclusions() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:identifiers;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(data);
        jdbc.execute("create table message_identifier_counter(id integer primary key, next_value integer)");
        jdbc.update("insert into message_identifier_counter values(1,1)");
        var transactions = new DataSourceTransactionManager(data);
        var service = new MessageIdentifierService(jdbc, transactions);
        assertThat(service.next()).isEqualTo("1");
        assertThat(new MessageIdentifierService(jdbc, transactions).next()).isEqualTo("2");
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<String>>();
            for (int i = 0; i < 200; i++) tasks.add(service::next);
            var values = new HashSet<String>();
            for (var result : executor.invokeAll(tasks)) values.add(result.get());
            assertThat(values).hasSize(200);
        }
        jdbc.update("update message_identifier_counter set next_value=35");
        assertThat(service.next()).isEqualTo("Z");
        assertThat(service.next()).isEqualTo("10");
        jdbc.update("update message_identifier_counter set next_value=60466175");
        assertThat(service.next()).isEqualTo("ZZZZZ");
        assertThat(service.next()).isEqualTo("0");
        var message = new StationMessage(null, "KC1VMZ-4", "WC1", null, null, "Welcome", "route", MessageType.MESSAGE);
        service.prepare(message);
        String number = message.getMessageIdentifier();
        service.prepare(message);
        assertThat(message.getMessageIdentifier()).isEqualTo(number);
        assertThat(MessageIdentifierService.wireContent(message)).isEqualTo("Welcome{" + number);
        message.setMessageIdentifier("00013");
        service.prepare(message);
        assertThat(message.getMessageIdentifier()).isEqualTo("00013");
        assertThat(MessageIdentifierService.wireContent(message)).isEqualTo("Welcome{00013");
        for (String text : new String[] {"ack00001", "rejAB123"}) {
            var control = new StationMessage(null, "KC1VMZ-4", "WC1", null, null, text, "route", MessageType.MESSAGE);
            service.prepare(control);
            assertThat(control.getMessageIdentifier()).isNull();
            assertThat(MessageIdentifierService.wireContent(control)).isEqualTo(text);
        }
        var bulletin = new StationMessage(null, "BLN1", "WC1", null, null, "Bulletin", "route", MessageType.BULLETIN);
        service.prepare(bulletin);
        assertThat(bulletin.getMessageIdentifier()).isNull();
    }
}
