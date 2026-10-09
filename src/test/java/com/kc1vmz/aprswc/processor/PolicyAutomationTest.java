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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.accessor.CommunicationPolicyAccessor;
import com.kc1vmz.aprswc.accessor.IgnoreStationAccessor;
import com.kc1vmz.aprswc.communication.CommunicationInstanceManager;
import com.kc1vmz.aprswc.content.PolicyContentService;
import com.kc1vmz.aprswc.content.TinyTopicsClient;
import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.enumeration.*;
import com.kc1vmz.aprswc.object.*;
import com.kc1vmz.aprswc.parser.PacketParser;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:policy-automation;DB_CLOSE_DELAY=-1",
            "aprs.policy-scheduler.initial-delay=3600000"
        })
class PolicyAutomationTest {
    @Autowired
    PolicyAutomationProcessor processor;

    @Autowired
    CommunicationPolicyAccessor accessor;

    @Autowired
    CommunicationPolicyRepository policies;

    @Autowired
    CommunicationCategoryRepository categories;

    @Autowired
    WelcomeCenterRepository centers;

    @Autowired
    StationRepository stations;

    @Autowired
    StationPositionRepository positions;

    @Autowired
    IgnoreStationAccessor ignoreStations;

    @Autowired
    IgnoreStationRepository ignored;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StationMessageRepository messages;

    @MockitoBean
    CommunicationInstanceManager communications;

    @MockitoBean
    TinyTopicsClient tinyTopics;

    @Autowired
    PolicyContentService content;

    private WelcomeCenter center;
    private CommunicationCategory category;
    private final String route = UUID.randomUUID().toString();

    @BeforeEach
    void setup() {
        policies.deleteAll();
        category = categories.save(
                new CommunicationCategory(null, UUID.randomUUID().toString(), "", false, false));
        center = centers.save(new WelcomeCenter(
                null,
                "Test",
                "",
                UUID.randomUUID().toString().substring(0, 6),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "c",
                "/",
                List.of(new WelcomeRegion(
                        null,
                        "Test",
                        "",
                        RegionType.CIRCLE,
                        null,
                        null,
                        null,
                        null,
                        "07254.00W",
                        "4336.00N",
                        10,
                        DistanceUnit.KILOMETERS))));
        when(communications.connectedRoutes(any())).thenReturn(List.of(route));
        when(communications.isEligible(any(), any())).thenReturn(true);
    }

    private CommunicationPolicy policy(CommunicationEventType type) {
        var settings = type == CommunicationEventType.SHRIEK_HEARD
                ? new PolicyAutomation("UTC", null, null, null, null, "!WC1!")
                : new PolicyAutomation(
                        "UTC",
                        LocalDateTime.now(ZoneOffset.UTC)
                                .plusDays(1)
                                .withSecond(0)
                                .withNano(0),
                        null,
                        null,
                        null,
                        null);
        return accessor.create(new CommunicationPolicy(
                        null, category, center, "Welcome", null, null, MessageType.MESSAGE, type, 0, settings, null))
                .block();
    }

    private void due(CommunicationPolicy p, Instant when) {
        jdbc.update("update communication_policies set next_run_at=? where id=?", Timestamp.from(when), p.getId());
    }

    private String outcome(CommunicationPolicy p) {
        return jdbc.queryForObject("select outcome from policy_executions where policy_id=?", String.class, p.getId());
    }

    private Station station(String callsign, String latitude) {
        var station = stations.save(new Station(null, callsign, StationState.STATIONARY, null));
        var position = new StationPosition(null, callsign, "07254.00W", latitude, LocalDateTime.now());
        position.setStation(station);
        positions.save(position);
        return station;
    }

    private StationPacket status(Station station, String text) {
        return new StationPacket(null, route, station.getCallsign(), null, ">" + text, null);
    }

    @Test
    void tinyTopicsPersistsParametersAndSharesContentAcrossRoutesAndRetries() {
        var p = policy(CommunicationEventType.SCHEDULED_ONCE);
        p.setContentSource(PolicyContentSource.TINYTOPICS);
        p.setTopicId("forecast");
        p.setTopicParameters(Map.of("x", "-72.97", "y", "43.61"));
        p.setMessageText(null);
        p = accessor.replace(p.getId(), p).block();
        assertThat(policies.findById(p.getId()).orElseThrow().getTopicParameters())
                .containsEntry("x", "-72.97");
        when(tinyTopics.resolve("forecast", p.getTopicParameters()))
                .thenReturn(new TinyTopicsClient.Result("CONTENT", 200, null, "Sunny today"));
        when(communications.connectedRoutes(any())).thenReturn(List.of(route, "second-route"));
        when(communications.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    assertThat(call.getArgument(3, String.class)).isEqualTo("Sunny today");
                    assertThat(call.getArgument(4, BooleanSupplier.class).getAsBoolean())
                            .isTrue();
                    call.getArgument(5, Runnable.class).run();
                    return true;
                });
        Instant now = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        due(p, now);
        processor.tick(now);
        verify(tinyTopics, times(1)).resolve("forecast", p.getTopicParameters());
        assertThat(content.resolve(p, "B:" + now)).isEqualTo("Sunny today");
        verify(tinyTopics, times(1)).resolve("forecast", p.getTopicParameters());
        assertThat(content.history(p.getId())).hasSize(1);
        assertThat(processor.executions(p.getId())).hasSize(2).allSatisfy(row -> assertThat(row.get("OUTCOME"))
                .isEqualTo("TRANSMITTED"));
    }

    @Test
    void tinyTopicsFailureHistorySurvivesSuccessfulFallbackTransmission() {
        var p = policy(CommunicationEventType.SCHEDULED_ONCE);
        p.setContentSource(PolicyContentSource.TINYTOPICS);
        p.setTopicId("forecast");
        p.setMessageText(null);
        p = accessor.replace(p.getId(), p).block();
        when(tinyTopics.resolve(eq("forecast"), any()))
                .thenReturn(
                        new TinyTopicsClient.Result("ERROR", 502, "Upstream unavailable", TinyTopicsClient.FALLBACK));
        when(communications.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    assertThat(call.getArgument(3, String.class)).isEqualTo(TinyTopicsClient.FALLBACK);
                    assertThat(call.getArgument(4, BooleanSupplier.class).getAsBoolean())
                            .isTrue();
                    call.getArgument(5, Runnable.class).run();
                    return true;
                });
        Instant now = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        due(p, now);
        processor.tick(now);
        assertThat(outcome(p)).isEqualTo("TRANSMITTED");
        assertThat(content.history(p.getId()).getFirst())
                .containsEntry("CONTENT_STATUS", "ERROR")
                .containsEntry("HTTP_STATUS", 502)
                .containsEntry("ERROR_MESSAGE", "Upstream unavailable");
        policies.deleteById(p.getId());
        assertThat(content.history(p.getId())).isEmpty();
    }

    @Test
    void ignoreShriekPersistsFullCallsignWithoutPolicyOrReply() {
        var station = station("N1WCI-4", "0036.00N");
        processor.statusHeard(status(station, "!wci!"), station);
        assertThat(ignoreStations.isIgnoreStation("N1WCI-4")).isFalse();
        processor.statusHeard(status(station, "Please ignore !WCI!"), station);
        processor.statusHeard(status(station, "!WCI!"), station);
        assertThat(ignoreStations.isIgnoreStation("N1WCI-4")).isTrue();
        assertThat(ignoreStations.isIgnoreStation("N1WCI")).isFalse();
        assertThat(ignoreStations.isIgnoreStation("N1WCI-5")).isFalse();
        assertThat(ignored.findAll().stream().filter(i -> i.getCallsign().equals("N1WCI-4")))
                .hasSize(1);
        var other = station("N1WCI-5", "0036.00N");
        processor.positionCommentHeard(
                new StationPacket(
                        null, route, other.getCallsign(), null, "=4222.88N/07157.29W$096/002/A=001023 !WCI!", null),
                other);
        assertThat(ignoreStations.isIgnoreStation("N1WCI-5")).isTrue();
        verify(communications, never()).sendMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void bulletinRecordsOnlyAfterWriteAndDoesNotRunAgain() {
        var p = policy(CommunicationEventType.SCHEDULED_ONCE);
        var guards = new ArrayList<BooleanSupplier>();
        var callbacks = new ArrayList<Runnable>();
        when(communications.sendMessage(eq(route), eq(center.getCallsign()), eq("BLN1"), eq("Welcome"), any(), any()))
                .thenAnswer(call -> {
                    guards.add(call.getArgument(4));
                    callbacks.add(call.getArgument(5));
                    return true;
                });
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        due(p, now);
        processor.tick(now);
        assertThat(outcome(p)).isEqualTo("CLAIMED");
        assertThat(jdbc.queryForObject(
                        "select transmitted_at from policy_executions where policy_id=?", Timestamp.class, p.getId()))
                .isNull();
        assertThat(guards.getFirst().getAsBoolean()).isTrue();
        assertThat(messages.findAllByWelcomeCenterIdOrderBySentTimeDesc(center.getId()))
                .isEmpty();
        callbacks.getFirst().run();
        callbacks.getFirst().run(); // A duplicate completion callback must not duplicate the log.
        var logged = messages.findAllByWelcomeCenterIdOrderBySentTimeDesc(center.getId());
        assertThat(logged).hasSize(1);
        assertThat(logged.getFirst().getCallsignTo()).isEqualTo("BLN1");
        assertThat(logged.getFirst().getCallsignFrom()).isEqualTo(center.getCallsign());
        assertThat(logged.getFirst().getPacketProcessorId()).isEqualTo(route);
        assertThat(logged.getFirst().getContent()).isEqualTo("Welcome");
        assertThat(logged.getFirst().getMessageType()).isEqualTo(MessageType.BULLETIN);
        assertThat(logged.getFirst().getSentTime()).isNotNull();
        assertThat(outcome(p)).isEqualTo("TRANSMITTED");
        processor.tick(now.plusSeconds(1));
        assertThat(callbacks).hasSize(1);
        assertThat(policies.findById(p.getId()).orElseThrow().getNextRunAt()).isNull();
    }

    @Test
    void skipsClosedUnavailableAndMissedOccurrencesAndInvalidatesQueuedEdit() {
        var closed = policy(CommunicationEventType.SCHEDULED_ONCE);
        center.setStatus(WelcomeCenterStatus.CLOSED);
        centers.save(center);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        due(closed, now);
        processor.tick(now);
        assertThat(outcome(closed)).isEqualTo("SKIPPED");
        center.setStatus(WelcomeCenterStatus.OPEN);
        centers.save(center);
        var missed = policy(CommunicationEventType.SCHEDULED_ONCE);
        due(missed, now.minusSeconds(120));
        processor.tick(now);
        assertThat(outcome(missed)).isEqualTo("SKIPPED");
        var offline = policy(CommunicationEventType.SCHEDULED_ONCE);
        when(communications.connectedRoutes(any())).thenReturn(List.of());
        due(offline, now);
        processor.tick(now);
        assertThat(outcome(offline)).isEqualTo("SKIPPED");
        when(communications.connectedRoutes(any())).thenReturn(List.of(route));
        var changed = policy(CommunicationEventType.SCHEDULED_ONCE);
        var guards = new ArrayList<BooleanSupplier>();
        when(communications.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    guards.add(call.getArgument(4));
                    return true;
                });
        due(changed, now);
        processor.tick(now);
        changed.setMessageText("Updated");
        accessor.replace(changed.getId(), changed).block();
        assertThat(guards.getFirst().getAsBoolean()).isFalse();
    }

    @Test
    void shriekIsCaseSensitiveGeographicAndOncePerFullCallsignPerDay() {
        var p = policy(CommunicationEventType.SHRIEK_HEARD);
        var station = station("N1SHRK", "4336.00N");
        var callbacks = new ArrayList<Runnable>();
        when(communications.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    assertThat(((BooleanSupplier) call.getArgument(4)).getAsBoolean())
                            .isTrue();
                    callbacks.add(call.getArgument(5));
                    return true;
                });
        processor.statusHeard(status(station, "!wc1!"), station);
        assertThat(callbacks).isEmpty();
        when(communications.isEligible(any(), any())).thenReturn(false);
        processor.statusHeard(status(station, "!WC1!"), station);
        assertThat(callbacks).isEmpty();
        when(communications.isEligible(any(), any())).thenReturn(true);
        processor.statusHeard(status(station, "hello !WC1!"), station);
        processor.statusHeard(status(station, "hello !WC1!"), station);
        assertThat(callbacks).hasSize(1);
        assertThat(messages.findAllByWelcomeCenterIdOrderBySentTimeDesc(center.getId()))
                .isEmpty();
        callbacks.getFirst().run();
        var logged = messages.findAllByWelcomeCenterIdOrderBySentTimeDesc(center.getId());
        assertThat(logged).hasSize(1);
        assertThat(logged.getFirst().getCallsignTo()).isEqualTo(station.getCallsign());
        assertThat(logged.getFirst().getMessageType()).isEqualTo(MessageType.MESSAGE);
        assertThat(logged.getFirst().getSentTime()).isNotNull();
        p.setMessageText("Edited");
        accessor.replace(p.getId(), p).block();
        processor.statusHeard(status(station, "!WC1!"), station);
        assertThat(callbacks).hasSize(1);
        var other = station("N1SHRK-1", "4336.00N");
        processor.statusHeard(status(other, "!WC1!"), other);
        assertThat(callbacks).hasSize(2);
        var outside = station("N2SHRK", "0036.00N");
        processor.statusHeard(status(outside, "!WC1!"), outside);
        assertThat(callbacks).hasSize(2);
    }

    @Test
    void positionCommentTriggersShriekAndSharesStatusDailyLimit() {
        var p = policy(CommunicationEventType.SHRIEK_HEARD);
        p.getAutomation().setShriekCode("!WC!");
        accessor.replace(p.getId(), p).block();
        var station = station("KC1VMZ-4", "4336.00N");
        var callbacks = new ArrayList<Runnable>();
        when(communications.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    assertThat(((BooleanSupplier) call.getArgument(4)).getAsBoolean())
                            .isTrue();
                    callbacks.add(call.getArgument(5));
                    return true;
                });
        var packet = new StationPacket(
                null,
                route,
                null,
                null,
                "KC1VMZ-4>APDR16,TCPIP*,qAC,T2SJC:=4222.88N/07157.29W$096/002/A=001023 APRSdroid and UV-PRO !WC!",
                null);
        assertThat(new PacketParser().parse(packet)).isEqualTo(PacketType.LOCATION);
        processor.positionCommentHeard(packet, station);
        assertThat(callbacks).hasSize(1);
        callbacks.getFirst().run();
        processor.statusHeard(status(station, "!WC!"), station);
        processor.positionCommentHeard(packet, station);
        assertThat(callbacks).hasSize(1);
    }

    @Test
    void unsuccessfulShriekDoesNotConsumeTheDayAndConnectionResultsAreIndependent() {
        var p = policy(CommunicationEventType.SHRIEK_HEARD);
        var station = station("N3SHRK", "4336.00N");
        when(communications.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenReturn(false);
        processor.statusHeard(status(station, "!WC1!"), station);
        assertThat(outcome(p)).isEqualTo("FAILED");
        when(communications.sendMessage(any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    assertThat(((BooleanSupplier) call.getArgument(4)).getAsBoolean())
                            .isTrue();
                    ((Runnable) call.getArgument(5)).run();
                    return true;
                });
        processor.statusHeard(status(station, "!WC1!"), station);
        assertThat(outcome(p)).isEqualTo("TRANSMITTED");
        processor.ready(); // Startup does not clear the durable daily limit.
        clearInvocations(communications);
        processor.statusHeard(status(station, "!WC1!"), station);
        verify(communications, never()).sendMessage(any(), any(), any(), any(), any(), any());
        var bulletin = policy(CommunicationEventType.SCHEDULED_ONCE);
        String otherRoute = UUID.randomUUID().toString();
        when(communications.connectedRoutes(any())).thenReturn(List.of(route, otherRoute));
        doReturn(false).when(communications).sendMessage(eq(otherRoute), any(), any(), any(), any(), any());
        Instant now = Instant.now().plusMillis(1).truncatedTo(ChronoUnit.MILLIS);
        due(bulletin, now);
        processor.tick(now);
        assertThat(processor.executions(bulletin.getId())).hasSize(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from policy_executions where policy_id=? and transmitted_at is not null",
                        Integer.class,
                        bulletin.getId()))
                .isEqualTo(1);
    }

    @Test
    void validationAndDeletionPreserveContainment() {
        var p = policy(CommunicationEventType.SCHEDULED_ONCE);
        var stale = policies.findById(p.getId()).orElseThrow();
        p.setMessageText("Changed");
        accessor.replace(p.getId(), p).block();
        assertThatThrownBy(() -> accessor.replace(stale.getId(), stale).block())
                .isInstanceOf(ResponseStatusException.class);
        when(communications.connectedRoutes(any())).thenReturn(List.of());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        due(p, now);
        processor.tick(now);
        assertThat(processor.executions(p.getId())).hasSize(1);
        accessor.delete(p.getId()).block();
        assertThat(processor.executions(p.getId())).isEmpty();
    }
}
