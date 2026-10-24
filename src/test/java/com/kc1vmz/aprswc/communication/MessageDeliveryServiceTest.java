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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.database.CommunicationInstanceRepository;
import com.kc1vmz.aprswc.database.CommunicationPolicyRepository;
import com.kc1vmz.aprswc.database.IgnoreStationRepository;
import com.kc1vmz.aprswc.database.WelcomeCenterRepository;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.enumeration.WelcomeCenterStatus;
import com.kc1vmz.aprswc.object.IgnoreStation;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.processor.StationPacketProcessor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:delivery-tests;DB_CLOSE_DELAY=-1",
            "aprs.delivery.initial-delay=3600000",
            "aprs.policy-scheduler.initial-delay=3600000"
        })
class MessageDeliveryServiceTest {
    @Autowired
    MessageDeliveryService service;

    @Autowired
    StationPacketProcessor packets;

    @Autowired
    MessageIdentifierService identifiers;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    ObjectProvider<CommunicationInstanceManager> managers;

    @Autowired
    WelcomeCenterRepository centers;

    @Autowired
    CommunicationPolicyRepository policies;

    @Autowired
    CommunicationInstanceRepository connections;

    @Autowired
    IgnoreStationRepository ignored;

    @MockitoBean
    CommunicationInstanceManager manager;

    private final ArrayList<BooleanSupplier> guards = new ArrayList<>();
    private final ArrayList<Runnable> callbacks = new ArrayList<>();
    private final ArrayList<String> wires = new ArrayList<>();
    private String route;

    @BeforeEach
    void setup() {
        jdbc.update("delete from message_deliveries");
        ignored.deleteAll();
        jdbc.update("update application_settings set message_max_retries=3,message_retry_interval_seconds=60");
        route = UUID.randomUUID().toString();
        jdbc.update(
                "insert into communication_instances(id,version,type,state) values(?,0,'APRS_IS','ACTIVE')",
                UUID.fromString(route));
        guards.clear();
        callbacks.clear();
        wires.clear();
        when(manager.health(any())).thenReturn(new CommunicationInstanceManager.Health("CONNECTED", null, null));
        when(manager.isEligible(any(), any())).thenReturn(true);
        when(manager.sendUntracked(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            wires.add(call.getArgument(3));
            guards.add(call.getArgument(4));
            callbacks.add(call.getArgument(5));
            return true;
        });
    }

    private StationMessage message() {
        var m = new StationMessage(null, "KC1VMZ-4", "WC1", null, null, "Welcome", route, MessageType.MESSAGE);
        identifiers.prepare(m);
        service.send(route, "WC1", "KC1VMZ-4", MessageIdentifierService.wireContent(m), () -> true, () -> {});
        due(m);
        service.tick(Instant.now());
        return m;
    }

    private void due(StationMessage m) {
        jdbc.update(
                "update message_deliveries set next_attempt=?,last_sent=case when last_sent is null then null else ? end where id=?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                Timestamp.from(Instant.now().minusSeconds(61)),
                m.getId());
    }

    private String state(StationMessage m) {
        return jdbc.queryForObject("select state from message_deliveries where id=?", String.class, m.getId());
    }

    private int attempts(StationMessage m) {
        return jdbc.queryForObject("select attempts from message_deliveries where id=?", Integer.class, m.getId());
    }

    private void write(int index) {
        assertThat(guards.get(index).getAsBoolean()).isTrue();
        callbacks.get(index).run();
    }

    @Test
    void retransmitsSamePacketAndStopsQueuedRetryOnAck() {
        var m = message();
        assertThat(attempts(m)).isZero();
        write(0);
        assertThat(state(m)).isEqualTo("AWAITING_ACK");
        due(m);
        service.tick(Instant.now());
        assertThat(wires).hasSize(2);
        assertThat(wires.get(1)).isEqualTo(wires.get(0));
        service.acknowledge("KC1VMZ-5", "WC1", "ack" + m.getMessageIdentifier());
        assertThat(state(m)).isEqualTo("AWAITING_ACK");
        assertThat(service.acknowledge("kc1vmz-4", "wc1", "ack" + m.getMessageIdentifier()))
                .isTrue();
        assertThat(guards.get(1).getAsBoolean()).isFalse();
        assertThat(state(m)).isEqualTo("ACKNOWLEDGED");
        assertThat(jdbc.queryForObject("select count(*) from station_messages where id=?", Integer.class, m.getId()))
                .isEqualTo(1);
    }

    @Test
    void ackWithAppendedMessageNumberStopsRetriesWithoutReply() {
        var m = message();
        write(0);
        due(m);
        service.tick(Instant.now());
        String ack = "ack" + m.getMessageIdentifier() + "{AB123";
        assertThat(service.acknowledge("KC1VMZ-5", "WC1", ack)).isTrue();
        assertThat(service.acknowledge("KC1VMZ-4", "WC2", ack)).isTrue();
        assertThat(state(m)).isEqualTo("AWAITING_ACK");
        assertThat(service.acknowledge("KC1VMZ-4", "WC1", ack + "X")).isFalse();
        assertThat(state(m)).isEqualTo("AWAITING_ACK");
        assertThat(service.acknowledge("KC1VMZ-4", "WC1", ack)).isTrue();
        assertThat(state(m)).isEqualTo("ACKNOWLEDGED");
        assertThat(guards.get(1).getAsBoolean()).isFalse();
        service.tick(Instant.now().plusSeconds(120));
        assertThat(wires).hasSize(2);
    }

    @Test
    void maximumRetriesExpirationAndLateAck() {
        var m = message();
        write(0);
        for (int i = 1; i <= 3; i++) {
            due(m);
            service.tick(Instant.now());
            write(i);
        }
        due(m);
        service.tick(Instant.now());
        assertThat(attempts(m)).isEqualTo(4);
        assertThat(state(m)).isEqualTo("EXHAUSTED");
        service.acknowledge("KC1VMZ-4", "WC1", "Reply{AB}" + m.getMessageIdentifier());
        assertThat(state(m)).isEqualTo("ACKNOWLEDGED");
        var expired = message();
        jdbc.update(
                "update message_deliveries set expires_at=? where id=?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                expired.getId());
        service.tick(Instant.now());
        assertThat(state(expired)).isEqualTo("EXHAUSTED");
    }

    @Test
    void failedWriteRestartAndRetentionDoNotLosePendingWork() {
        var m = message();
        assertThat(guards.getFirst().getAsBoolean()).isTrue(); // No completion callback: write failed.
        assertThat(attempts(m)).isZero();
        var restarted =
                new MessageDeliveryService(jdbc, transactions, managers, centers, policies, connections, ignored);
        restarted.start();
        due(m);
        restarted.tick(Instant.now());
        write(1);
        assertThat(attempts(m)).isEqualTo(1);
        jdbc.update("delete from station_messages where id=?", m.getId());
        due(m);
        restarted.tick(Instant.now());
        write(2);
        assertThat(attempts(m)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from station_messages where id=?", Integer.class, m.getId()))
                .isZero();
        restarted.acknowledge("KC1VMZ-4", "WC1", "rej" + m.getMessageIdentifier());
        assertThat(state(m)).isEqualTo("REJECTED");
    }

    @Test
    void pausedDeletedIgnoredAndSettingsChangesCancelOrExhaust() {
        var m = message();
        write(0);
        ignored.save(new IgnoreStation(null, "KC1VMZ-4"));
        service.tick(Instant.now());
        assertThat(state(m)).isEqualTo("CANCELLED");
        ignored.deleteAll();
        var paused = message();
        jdbc.update("update communication_instances set state='PAUSED' where id=?", UUID.fromString(route));
        service.tick(Instant.now());
        assertThat(state(paused)).isEqualTo("CANCELLED");
        jdbc.update("update communication_instances set state='ACTIVE' where id=?", UUID.fromString(route));
        var noRetry = message();
        write(callbacks.size() - 1);
        jdbc.update("update application_settings set message_max_retries=0");
        due(noRetry);
        service.tick(Instant.now());
        assertThat(state(noRetry)).isEqualTo("EXHAUSTED");
        var deleted = message();
        connections.deleteById(UUID.fromString(route));
        service.tick(Instant.now());
        assertThat(state(deleted)).isEqualTo("CANCELLED");
    }

    @Test
    void ackRacingWithWriteIsCountedAndOfflineDoesNotConsumeAttempts() {
        var m = message();
        assertThat(guards.getFirst().getAsBoolean()).isTrue();
        service.acknowledge("KC1VMZ-4", "WC1", "ack" + m.getMessageIdentifier());
        callbacks.getFirst().run();
        assertThat(attempts(m)).isEqualTo(1);
        assertThat(state(m)).isEqualTo("ACKNOWLEDGED");
        when(manager.health(any())).thenReturn(new CommunicationInstanceManager.Health("RETRYING", null, null));
        var offline = message();
        service.tick(Instant.now().plusSeconds(100));
        assertThat(attempts(offline)).isZero();
        assertThat(state(offline)).isEqualTo("PENDING");
    }

    @Test
    void centerClosurePolicyChangeAndActiveIdentifierCollision() {
        // Use the delivery snapshot's policy ID to verify deletion cancels even before first write.
        var m = message();
        jdbc.update(
                "update message_deliveries set policy_id=?,policy_version=0 where id=?", UUID.randomUUID(), m.getId());
        service.tick(Instant.now());
        assertThat(state(m)).isEqualTo("CANCELLED");
        var closed = message();
        var center = centers.save(new WelcomeCenter(
                null, "Delivery center", "", "WC1", null, null, null, null, null, null, null, null, "c", "/", null));
        center.setStatus(WelcomeCenterStatus.CLOSED);
        centers.save(center);
        jdbc.update("update message_deliveries set center_id=? where id=?", center.getId(), closed.getId());
        service.tick(Instant.now());
        assertThat(state(closed)).isEqualTo("CANCELLED");
        var ack = new StationPacket(
                null,
                UUID.randomUUID().toString(),
                "KC1VMZ-4",
                null,
                ":WC1      :ack" + closed.getMessageIdentifier(),
                null);
        ReflectionTestUtils.invokeMethod(packets, "processMessagePacket", ack, null);
        assertThat(state(closed)).isEqualTo("ACKNOWLEDGED");
        var pending = message();
        jdbc.update(
                "update message_identifier_counter set next_value=? where id=1",
                Integer.parseInt(pending.getMessageIdentifier(), 36));
        assertThat(identifiers.next()).isNotEqualTo(pending.getMessageIdentifier());
    }
}
