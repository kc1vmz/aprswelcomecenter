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

import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.object.StationMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Persistent directed-message delivery, independent of message-log retention. */
@Service
public class MessageDeliveryService {
    private static final Logger LOG = LoggerFactory.getLogger(MessageDeliveryService.class);
    private static final String ACTIVE = "state in ('PENDING','AWAITING_ACK')";
    private static final Pattern CONTROL = Pattern.compile("^(ack|rej)([A-Za-z0-9]{1,5})(?:}([A-Za-z0-9]{0,5}))?$");
    private static final Pattern REPLY = Pattern.compile("\\{[A-Za-z0-9]{1,5}}([A-Za-z0-9]{1,5})$");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectProvider<CommunicationInstanceManager> managers;
    private final WelcomeCenterRepository centers;
    private final CommunicationPolicyRepository policies;
    private final CommunicationInstanceRepository connections;
    private final IgnoreStationRepository ignored;
    private final Map<UUID, Hooks> hooks;
    private volatile boolean ready;

    private record Hooks(BooleanSupplier permitted, Runnable sent) {}

    private record Delivery(
            UUID id,
            String number,
            String from,
            String to,
            String route,
            UUID center,
            UUID policy,
            Long version,
            String content,
            Instant first,
            Instant last,
            Instant expires,
            String state,
            int attempts) {}

    public MessageDeliveryService(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            ObjectProvider<CommunicationInstanceManager> managers,
            WelcomeCenterRepository centers,
            CommunicationPolicyRepository policies,
            CommunicationInstanceRepository connections,
            IgnoreStationRepository ignored) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.managers = managers;
        this.centers = centers;
        this.policies = policies;
        this.connections = connections;
        this.ignored = ignored;
        this.hooks = new ConcurrentHashMap<>();
        this.ready = false;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        jdbc.update("update message_deliveries set armed=true,lease=null,lease_until=null where " + ACTIVE);
        ready = true;
    }

    public void register(StationMessage m, UUID policy, Long version, String occurrence, UUID claim) {
        if (m.getMessageIdentifier() == null) return;
        if (m.getId() == null) m.setId(UUID.randomUUID());
        Instant now = Instant.now();
        int inserted = jdbc.update(
                "insert into message_deliveries(id,identifier,sender,recipient,route,center_id,policy_id,policy_version,occurrence_key,claim_token,content,created_at,next_attempt,expires_at,state) select ?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING' where not exists(select 1 from message_deliveries where id=?)",
                m.getId(),
                m.getMessageIdentifier(),
                m.getCallsignFrom().toUpperCase(Locale.ROOT),
                m.getCallsignTo().toUpperCase(Locale.ROOT),
                m.getPacketProcessorId(),
                m.getWelcomeCenter() == null ? null : m.getWelcomeCenter().getId(),
                policy,
                version,
                occurrence,
                claim,
                m.getContent(),
                Timestamp.from(now),
                Timestamp.from(now.plusSeconds(2)),
                Timestamp.from(now.plusSeconds(900)),
                m.getId());
        if (inserted == 1) {
            jdbc.update(
                    "update station_messages set message_identifier=? where id=?", m.getMessageIdentifier(), m.getId());
            jdbc.update(
                    "insert into station_messages(id,callsign_from,callsign_to,welcome_center_id,created_time,content,packet_processor_id,message_type,message_identifier) select ?,?,?,?,?,?,?,'MESSAGE',? where not exists(select 1 from station_messages where id=?)",
                    m.getId(),
                    m.getCallsignFrom(),
                    m.getCallsignTo(),
                    m.getWelcomeCenter() == null ? null : m.getWelcomeCenter().getId(),
                    Timestamp.from(now),
                    m.getContent(),
                    m.getPacketProcessorId(),
                    m.getMessageIdentifier(),
                    m.getId());
        }
    }

    public boolean send(String route, String from, String to, String wire, BooleanSupplier permitted, Runnable sent) {
        int brace = wire == null ? -1 : wire.lastIndexOf('{');
        if (brace < 0) return managers.getObject().sendUntracked(route, from, to, wire, permitted, sent);
        var ids = jdbc.queryForList(
                "select id from message_deliveries where sender=? and recipient=? and identifier=? order by created_at desc fetch first 1 row only",
                UUID.class,
                from.toUpperCase(Locale.ROOT),
                to.toUpperCase(Locale.ROOT),
                wire.substring(brace + 1));
        if (ids.isEmpty()) return managers.getObject().sendUntracked(route, from, to, wire, permitted, sent);
        UUID id = ids.getFirst();
        hooks.putIfAbsent(id, new Hooks(permitted, sent));
        jdbc.update(
                "update message_deliveries set armed=true,next_attempt=? where id=? and state='PENDING' and armed=false",
                Timestamp.from(Instant.now()),
                id);
        attempt(id, Instant.now());
        return true; // Accepted durably, including while an active connection is temporarily offline.
    }

    private Delivery get(UUID id) {
        var values = jdbc.query(
                "select * from message_deliveries where id=?",
                (rs, n) -> new Delivery(
                        rs.getObject("id", UUID.class),
                        rs.getString("identifier"),
                        rs.getString("sender"),
                        rs.getString("recipient"),
                        rs.getString("route"),
                        rs.getObject("center_id", UUID.class),
                        rs.getObject("policy_id", UUID.class),
                        rs.getObject("policy_version", Long.class),
                        rs.getString("content"),
                        rs.getTimestamp("first_sent") == null
                                ? null
                                : rs.getTimestamp("first_sent").toInstant(),
                        rs.getTimestamp("last_sent") == null
                                ? null
                                : rs.getTimestamp("last_sent").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getString("state"),
                        rs.getInt("attempts")),
                id);
        return values.isEmpty() ? null : values.getFirst();
    }

    private boolean active(Delivery d) {
        return d != null && (d.state().equals("PENDING") || d.state().equals("AWAITING_ACK"));
    }

    private int setting(String column, int fallback) {
        var rows = jdbc.queryForList(
                "select " + column + " from application_settings fetch first 1 row only", Integer.class);
        return rows.isEmpty() ? fallback : rows.getFirst();
    }

    private boolean eligible(Delivery d) {
        if (ignored.findByCallsignIgnoreCase(d.to()).isPresent()) return false;
        UUID route;
        try {
            route = UUID.fromString(d.route());
        } catch (RuntimeException e) {
            return false;
        }
        var connection = connections.findById(route).orElse(null);
        if (connection == null || !"ACTIVE".equals(connection.getState())) return false;
        if (d.center() != null) {
            var center = centers.findById(d.center()).orElse(null);
            if (center == null
                    || !center.isOpen()
                    || !center.getCallsign().equalsIgnoreCase(d.from())
                    || !managers.getObject().isEligible(d.center(), d.route())) return false;
        }
        if (d.policy() != null) {
            var policy = policies.findById(d.policy()).orElse(null);
            if (policy == null || !Objects.equals(policy.getVersion(), d.version())) return false;
        }
        return true;
    }

    private void finish(UUID id, String state, Instant now) {
        jdbc.update(
                "update message_deliveries set state=?,completed_at=? where id=? and " + ACTIVE,
                state,
                Timestamp.from(now),
                id);
        hooks.remove(id);
    }

    @Scheduled(
            fixedDelayString = "${aprs.delivery.delay:1000}",
            initialDelayString = "${aprs.delivery.initial-delay:1000}")
    public void poll() {
        if (ready) tick(Instant.now());
    }

    public void tick(Instant now) {
        for (UUID id : jdbc.queryForList("select id from message_deliveries where " + ACTIVE, UUID.class)) {
            try {
                attempt(id, now);
            } catch (RuntimeException e) {
                LOG.error("Message delivery processing failed for {}", id, e);
            }
        }
    }

    private void attempt(UUID id, Instant now) {
        Delivery d = get(id);
        if (!active(d)) {
            hooks.remove(id);
            return;
        }
        if (!eligible(d)) {
            finish(id, "CANCELLED", now);
            return;
        }
        if (!now.isBefore(d.expires())) {
            finish(id, "EXHAUSTED", now);
            return;
        }
        int interval = setting("message_retry_interval_seconds", 60);
        if (d.last() != null && now.isBefore(d.last().plusSeconds(interval))) return;
        if (d.attempts() > setting("message_max_retries", 3)) {
            finish(id, "EXHAUSTED", now);
            return;
        }
        if (!"CONNECTED"
                .equals(managers.getObject().health(UUID.fromString(d.route())).status())) return;
        UUID lease = UUID.randomUUID();
        int claimed = jdbc.update(
                "update message_deliveries set lease=?,lease_until=? where id=? and " + ACTIVE
                        + " and armed=true and next_attempt<=? and (lease is null or lease_until<=?)",
                lease,
                Timestamp.from(now.plusSeconds(60)),
                id,
                Timestamp.from(now),
                Timestamp.from(now));
        if (claimed == 0) return;
        boolean queued = managers.getObject()
                .sendUntracked(
                        d.route(),
                        d.from(),
                        d.to(),
                        d.content() + "{" + d.number(),
                        () -> beforeWrite(id, lease),
                        () -> written(id, lease));
        if (!queued)
            jdbc.update(
                    "update message_deliveries set lease=null,lease_until=null,next_attempt=? where id=? and lease=?",
                    Timestamp.from(now.plusSeconds(5)),
                    id,
                    lease);
    }

    private boolean beforeWrite(UUID id, UUID lease) {
        Delivery current = get(id);
        Instant now = Instant.now();
        if (!active(current)) return false;
        if (!eligible(current)) {
            finish(id, "CANCELLED", now);
            return false;
        }
        if (!now.isBefore(current.expires())) {
            finish(id, "EXHAUSTED", now);
            return false;
        }
        if (current.attempts() > setting("message_max_retries", 3)) {
            finish(id, "EXHAUSTED", now);
            return false;
        }
        if (jdbc.queryForObject(
                        "select count(*) from message_deliveries where id=? and lease=? and lease_until>?",
                        Integer.class,
                        id,
                        lease,
                        Timestamp.from(now))
                != 1) return false;
        if (current.last() != null
                && now.isBefore(current.last().plusSeconds(setting("message_retry_interval_seconds", 60))))
            return false;
        jdbc.update(
                "update policy_executions set expires_at=? where (policy_id,occurrence_key,claim_token) in (select policy_id,occurrence_key,claim_token from message_deliveries where id=?) and outcome in ('CLAIMED','WRITING')",
                Timestamp.from(now.plusSeconds(60)),
                id);
        Hooks h = hooks.get(id);
        if (current.first() == null && h != null && !h.permitted().getAsBoolean()) {
            finish(id, "CANCELLED", now);
            return false;
        }
        // The original guard can claim a policy occurrence; evaluate it only once, even after write failure.
        if (h != null) hooks.replace(id, h, new Hooks(() -> true, h.sent()));
        return true;
    }

    private void written(UUID id, UUID lease) {
        Instant now = Instant.now();
        Boolean first = tx.execute(status -> {
            jdbc.queryForObject("select id from message_deliveries where id=? for update", UUID.class, id);
            Delivery d = get(id);
            if (jdbc.queryForObject(
                            "select count(*) from message_deliveries where id=? and lease=?", Integer.class, id, lease)
                    != 1) return false;
            jdbc.update(
                    "update message_deliveries set attempts=attempts+1,first_sent=coalesce(first_sent,?),last_sent=?,expires_at=case when first_sent is null then ? else expires_at end,next_attempt=?,state=case when state in ('PENDING','AWAITING_ACK') then 'AWAITING_ACK' else state end,lease=null,lease_until=null where id=?",
                    Timestamp.from(now),
                    Timestamp.from(now),
                    Timestamp.from(now.plusSeconds(900)),
                    Timestamp.from(now),
                    id);
            if (d.first() == null) {
                // Persist a log only on transmission; do not recreate a log removed by retention on subsequent
                // attempts.
                int updated = jdbc.update(
                        "update station_messages set sent_time=?,message_identifier=? where id=?",
                        Timestamp.from(now),
                        d.number(),
                        id);
                if (updated == 0 && (d.center() == null || centers.existsById(d.center())))
                    jdbc.update(
                            "insert into station_messages(id,callsign_from,callsign_to,welcome_center_id,sent_time,created_time,content,packet_processor_id,message_type,message_identifier) values(?,?,?,?,?,?,?,?,'MESSAGE',?)",
                            id,
                            d.from(),
                            d.to(),
                            d.center(),
                            Timestamp.from(now),
                            Timestamp.from(now),
                            d.content(),
                            d.route(),
                            d.number());
                jdbc.update(
                        "update policy_executions set outcome='TRANSMITTED',transmitted_at=? where (policy_id,occurrence_key,claim_token) in (select policy_id,occurrence_key,claim_token from message_deliveries where id=?)",
                        Timestamp.from(now),
                        id);
            }
            return d.first() == null;
        });
        Hooks h = hooks.remove(id);
        if (Boolean.TRUE.equals(first) && h != null) h.sent().run();
    }

    /** Called before command, center-state, and incoming-route filtering. */
    public boolean acknowledge(String from, String to, String text) {
        if (from == null || to == null || text == null) return false;
        var control = CONTROL.matcher(text);
        if (control.matches()) {
            complete(from, to, control.group(2), control.group(1).equals("ack") ? "ACKNOWLEDGED" : "REJECTED");
            return true;
        }
        var reply = REPLY.matcher(text);
        if (reply.find()) complete(from, to, reply.group(1), "ACKNOWLEDGED");
        return false;
    }

    private void complete(String from, String to, String number, String state) {
        var ids = jdbc.queryForList(
                "select id from message_deliveries where sender=? and recipient=? and identifier=? order by created_at desc fetch first 1 row only",
                UUID.class,
                to.toUpperCase(Locale.ROOT),
                from.toUpperCase(Locale.ROOT),
                number);
        if (ids.isEmpty()) return;
        UUID id = ids.getFirst();
        // Preserve an in-flight lease so its successful write is still counted if ACK raced with the callback.
        jdbc.update(
                "update message_deliveries set state=?,completed_at=? where id=? and state <> 'ACKNOWLEDGED'",
                state,
                Timestamp.from(Instant.now()),
                id);
        hooks.remove(id);
    }

    public List<Map<String, Object>> history() {
        return jdbc.queryForList(
                "select id,state,attempts,first_sent,last_sent,completed_at,expires_at from message_deliveries");
    }
}
