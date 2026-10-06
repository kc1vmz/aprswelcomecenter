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
package com.kc1vmz.aprswc.processor;

import com.kc1vmz.aprswc.accessor.IgnoreStationAccessor;
import com.kc1vmz.aprswc.communication.CommunicationInstanceManager;
import com.kc1vmz.aprswc.database.CommunicationPolicyRepository;
import com.kc1vmz.aprswc.database.StationMessageRepository;
import com.kc1vmz.aprswc.database.StationPositionRepository;
import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.utils.GeoFenceUtils;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable occurrence claims. A transmission is recorded only after the transport write returns. */
@Component
public class PolicyAutomationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyAutomationProcessor.class);
    private final CommunicationPolicyRepository policies;
    private final StationPositionRepository positions;
    private final StationMessageRepository messages;
    private final IgnoreStationAccessor ignoreStations;
    private final GeoFenceUtils fences;
    private final CommunicationInstanceManager communications;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private volatile Instant startedAt;

    public PolicyAutomationProcessor(
            CommunicationPolicyRepository policies,
            StationPositionRepository positions,
            StationMessageRepository messages,
            IgnoreStationAccessor ignoreStations,
            GeoFenceUtils fences,
            CommunicationInstanceManager communications,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions) {
        this.policies = policies;
        this.positions = positions;
        this.messages = messages;
        this.ignoreStations = ignoreStations;
        this.fences = fences;
        this.communications = communications;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
        this.startedAt = null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        startedAt = Instant.now();
    }

    @Scheduled(
            fixedDelayString = "${aprs.policy-scheduler.delay:1000}",
            initialDelayString = "${aprs.policy-scheduler.initial-delay:1000}")
    public void poll() {
        if (startedAt == null) return;
        try {
            tick(Instant.now());
        } catch (RuntimeException error) {
            LOG.error("Policy scheduler failed", error);
        }
    }

    public void tick(Instant now) {
        jdbc.update(
                "update policy_executions set outcome='FAILED' where outcome in ('CLAIMED','WRITING') and expires_at < ?",
                Timestamp.from(now));
        List<UUID> due = jdbc.query(
                "select id from communication_policies where next_run_at <= ?",
                (row, n) -> row.getObject(1, UUID.class),
                Timestamp.from(now));
        for (UUID id : due) {
            try {
                var occurrence = transaction.execute(tx -> {
                    lock();
                    var p = policies.findById(id).orElse(null);
                    if (p == null
                            || p.getNextRunAt() == null
                            || p.getNextRunAt().isAfter(now)) return null;
                    Instant dueAt = p.getNextRunAt();
                    Instant next = p.getCommunicationEventType() == CommunicationEventType.SCHEDULED_RECURRING
                            ? PolicySchedule.next(p, now)
                            : null;
                    jdbc.update(
                            "update communication_policies set next_run_at=? where id=?",
                            next == null ? null : Timestamp.from(next),
                            id);
                    return new Occurrence(p, dueAt);
                });
                if (occurrence == null) continue;
                var p = occurrence.policy();
                Instant dueAt = occurrence.time();
                boolean missed =
                        (startedAt != null && dueAt.isBefore(startedAt)) || dueAt.isBefore(now.minusSeconds(60));
                List<String> routes = missed || !p.getWelcomeCenter().isOpen()
                        ? List.of()
                        : communications.connectedRoutes(p.getWelcomeCenter().getId());
                if (routes.isEmpty()) {
                    recordSkipped(p, "B:" + dueAt, dueAt, now);
                    continue;
                }
                for (String route : routes) dispatch(p, "B:" + dueAt + ":" + route, route, "BLN1", null, dueAt, now);
            } catch (RuntimeException error) {
                LOG.error("Scheduled policy {} failed", id, error);
            }
        }
    }

    public void statusHeard(StationPacket packet, Station station) {
        String text = packet.getCommand();
        if (text == null || !text.startsWith(">")) return;
        shriekHeard(packet, station, text.substring(1));
    }

    public void positionCommentHeard(StationPacket packet, Station station) {
        String text = packet.getCommand();
        if (text == null || text.isEmpty() || "!=/@".indexOf(text.charAt(0)) < 0) return;
        int start = text.charAt(0) == '/' || text.charAt(0) == '@' ? 8 : 1;
        if (text.length() <= start) return;
        // Uncompressed coordinates occupy 19 characters; compressed positions occupy 13.
        int commentStart = start + (Character.isDigit(text.charAt(start)) ? 19 : 13);
        if (text.length() <= commentStart) return;
        shriekHeard(packet, station, text.substring(commentStart));
    }

    private void shriekHeard(StationPacket packet, Station station, String text) {
        Instant now = Instant.now();
        String callsign = station.getCallsign().toUpperCase(Locale.ROOT);
        if (text.contains("!WCI!")) {
            ignoreStations.createIfAbsent(callsign).block();
            return;
        }
        for (var p : policies.findAll()) {
            if (p.getCommunicationEventType() != CommunicationEventType.SHRIEK_HEARD) continue;
            try {
                var a = p.getAutomation();
                if (a == null || a.getShriekCode() == null || !text.contains(a.getShriekCode())) continue;
                if (!p.getWelcomeCenter().isOpen()
                        || !communications.isEligible(p.getWelcomeCenter().getId(), packet.getPacketProcessorId())
                        || !inside(p, station.getId())) continue;
                String key = "S:" + callsign + ":"
                        + now.atZone(ZoneId.of(a.getTimeZone())).toLocalDate();
                dispatch(p, key, packet.getPacketProcessorId(), callsign, station.getId(), now, now);
            } catch (RuntimeException error) {
                LOG.error("Shriek policy {} failed", p.getId(), error);
            }
        }
    }

    private boolean inside(CommunicationPolicy p, UUID stationId) {
        var recent = positions.findRecentByStationId(stationId, PageRequest.of(0, 1));
        return !recent.isEmpty()
                && p.getWelcomeCenter().getRegions().stream()
                        .anyMatch(region -> fences.isInGeoFenceRegion(region, recent.getFirst()));
    }

    private void lock() {
        jdbc.queryForObject("select id from aprs_object_name_lock where id=1 for update", Integer.class);
    }

    private UUID claim(CommunicationPolicy p, String key, String route, String callsign, Instant due, Instant now) {
        return transaction.execute(tx -> {
            lock();
            var current = policies.findById(p.getId()).orElse(null);
            if (current == null || current.getVersion() != p.getVersion()) return null;
            var rows = jdbc.queryForList(
                    "select outcome, expires_at from policy_executions where policy_id=? and occurrence_key=?",
                    p.getId(),
                    key);
            if (!rows.isEmpty()) {
                String outcome = rows.getFirst().get("OUTCOME").toString();
                if (!"FAILED".equals(outcome)) return null;
            }
            UUID token = UUID.randomUUID();
            jdbc.update(
                    "delete from policy_executions where policy_id=? and occurrence_key=? and outcome='FAILED'",
                    p.getId(),
                    key);
            jdbc.update(
                    "insert into policy_executions(policy_id,occurrence_key,instance_id,station_callsign,policy_version,claim_token,outcome,scheduled_for,expires_at) values(?,?,?,?,?,?,'CLAIMED',?,?)",
                    p.getId(),
                    key,
                    route,
                    callsign,
                    p.getVersion(),
                    token,
                    Timestamp.from(due),
                    Timestamp.from(now.plusSeconds(60)));
            return token;
        });
    }

    private void dispatch(
            CommunicationPolicy p, String key, String route, String to, UUID stationId, Instant due, Instant now) {
        UUID token = claim(p, key, route, stationId == null ? null : to, due, now);
        if (token == null) return;
        boolean queued = communications.sendMessage(
                route,
                p.getWelcomeCenter().getCallsign(),
                to,
                p.getMessageText(),
                () -> permitted(p, key, token, route, stationId),
                () -> transaction.executeWithoutResult(tx -> {
                    Instant sent = Instant.now();
                    int recorded = jdbc.update(
                            "update policy_executions set outcome='TRANSMITTED', transmitted_at=? where policy_id=? and occurrence_key=? and claim_token=? and outcome <> 'TRANSMITTED'",
                            Timestamp.from(sent),
                            p.getId(),
                            key,
                            token);
                    if (recorded == 1) {
                        messages.save(new StationMessage(
                                null,
                                to,
                                p.getWelcomeCenter().getCallsign(),
                                p.getWelcomeCenter(),
                                LocalDateTime.ofInstant(sent, ZoneId.systemDefault()),
                                p.getMessageText(),
                                route,
                                stationId == null ? MessageType.BULLETIN : MessageType.MESSAGE));
                    }
                }));
        if (!queued)
            jdbc.update(
                    "update policy_executions set outcome='FAILED' where policy_id=? and occurrence_key=? and claim_token=?",
                    p.getId(),
                    key,
                    token);
    }

    private boolean permitted(CommunicationPolicy expected, String key, UUID token, String route, UUID stationId) {
        var current = policies.findById(expected.getId()).orElse(null);
        if (current == null
                || current.getVersion() != expected.getVersion()
                || !current.getWelcomeCenter().isOpen()
                || !current.getWelcomeCenter()
                        .getCallsign()
                        .equals(expected.getWelcomeCenter().getCallsign())
                || !communications.isEligible(current.getWelcomeCenter().getId(), route)) return false;
        if (stationId != null) {
            if (!inside(current, stationId)) return false;
            String day = Instant.now()
                    .atZone(ZoneId.of(current.getAutomation().getTimeZone()))
                    .toLocalDate()
                    .toString();
            if (!key.endsWith(":" + day)) return false;
        }
        return jdbc.update(
                        "update policy_executions set outcome='WRITING' where policy_id=? and occurrence_key=? and claim_token=? and outcome='CLAIMED' and expires_at>=?",
                        expected.getId(),
                        key,
                        token,
                        Timestamp.from(Instant.now()))
                == 1;
    }

    private void recordSkipped(CommunicationPolicy p, String key, Instant due, Instant now) {
        UUID token = claim(p, key, null, null, due, now);
        if (token != null)
            jdbc.update(
                    "update policy_executions set outcome='SKIPPED' where policy_id=? and occurrence_key=? and claim_token=?",
                    p.getId(),
                    key,
                    token);
    }

    public List<Map<String, Object>> executions(UUID policyId) {
        return jdbc.queryForList(
                "select occurrence_key,instance_id,station_callsign,outcome,scheduled_for,transmitted_at from policy_executions where policy_id=? order by scheduled_for desc fetch first 50 rows only",
                policyId);
    }

    private record Occurrence(CommunicationPolicy policy, Instant time) {}
}
