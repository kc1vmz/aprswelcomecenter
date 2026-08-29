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
package com.kc1vmz.aprswc.object;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kc1vmz.aprswc.enumeration.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class DomainObjectsTest {
    @Test
    void welcomeCenterAndRegionAccessors() {
        WelcomeCenter c = new WelcomeCenter(
                UUID.randomUUID(),
                "North",
                "Description",
                "KC1VMZ",
                "Owner",
                "W1OWN",
                "Org",
                "W1ORG",
                "Contact",
                "W1CON",
                "-71.0589",
                "42.3601",
                "c",
                "/",
                List.of());
        WelcomeRegion r = new WelcomeRegion(
                UUID.randomUUID(),
                "Area",
                "Region",
                RegionType.CIRCLE,
                null,
                null,
                null,
                null,
                "07100.00W",
                "4200.00N",
                5,
                DistanceUnit.MILES);
        c.addRegion(r);
        assertAll(
                () -> assertEquals("KC1VMZ", c.getCallsign()),
                () -> assertEquals("-71.0589", c.getLongitude()),
                () -> assertEquals("42.3601", c.getLatitude()),
                () -> assertEquals("c", c.getSymbolCode()),
                () -> assertEquals("/", c.getSymbolId()),
                () -> assertEquals(1, c.getRegions().size()),
                () -> assertSame(c, r.getWelcomeCenter()),
                () -> assertEquals("07100.00W", r.getCenterLongitude()),
                () -> assertEquals("4200.00N", r.getCenterLatitude()),
                () -> assertEquals(5, r.getDiameter()));
        c.removeRegion(r);
        assertNull(r.getWelcomeCenter());
    }

    @Test
    void stationAndPositionAccessors() {
        Station s = new Station(UUID.randomUUID(), "N1ABC", StationState.MOVING, List.of());
        StationPosition p =
                new StationPosition(UUID.randomUUID(), "NOCALL-1", "07106.00W", "4218.00N", LocalDateTime.now());
        s.addPosition(p);
        assertAll(
                () -> assertEquals(StationState.MOVING, s.getState()),
                () -> assertEquals(s.getId(), p.getStationId()),
                () -> assertEquals("NOCALL-1", p.getCallsign()),
                () -> assertEquals("07106.00W", p.getLongitude()),
                () -> assertEquals("4218.00N", p.getLatitude()));
    }

    @Test
    void stationPacketUsesCurrentTimeWhenReceivedTimeIsNull() {
        LocalDateTime beforeCreation = LocalDateTime.now();
        StationPacket packet = new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", null, "raw", null);
        LocalDateTime afterCreation = LocalDateTime.now();

        assertAll(
                () -> assertNotNull(packet.getReceivedTime()),
                () -> assertFalse(packet.getReceivedTime().isBefore(beforeCreation)),
                () -> assertFalse(packet.getReceivedTime().isAfter(afterCreation)));
    }

    @Test
    void stationPacketPreservesProvidedReceivedTime() {
        LocalDateTime receivedTime = LocalDateTime.of(2026, 8, 21, 12, 30);
        StationPacket packet = new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", receivedTime, "raw", null);

        assertEquals(receivedTime, packet.getReceivedTime());
    }

    @Test
    void stationPacketConvertsHexHeaderToBytes() {
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "raw", "007FaBcD");

        assertArrayEquals(new byte[] {0x00, 0x7F, (byte) 0xAB, (byte) 0xCD}, packet.convertHeaderToBytes());
    }

    @Test
    void stationPacketRejectsInvalidHexHeaders() {
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "raw", "123");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, packet::convertHeaderToBytes),
                () -> {
                    packet.setHeader("12XZ");
                    assertThrows(IllegalArgumentException.class, packet::convertHeaderToBytes);
                },
                () -> {
                    packet.setHeader(null);
                    assertArrayEquals(new byte[0], packet.convertHeaderToBytes());
                });
    }

    @Test
    void stationPacketCallsignToIsInMemoryOnly() throws Exception {
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "raw", null);
        packet.setCallsignTo("KC1VMZ");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        String serialized = mapper.writeValueAsString(packet);
        StationPacket deserialized = mapper.readValue(
                "{\"callsign\":\"N1ABC\",\"callsignTo\":\"KC1VMZ\",\"command\":\"raw\"}", StationPacket.class);

        assertAll(
                () -> assertEquals("KC1VMZ", packet.getCallsignTo()),
                () -> assertFalse(serialized.contains("callsignTo")),
                () -> assertNull(deserialized.getCallsignTo()),
                () -> assertTrue(StationPacket.class
                        .getDeclaredField("callsignTo")
                        .isAnnotationPresent(jakarta.persistence.Transient.class)));
    }

    @Test
    void stationPacketWelcomeCenterIdIsInMemoryOnly() throws Exception {
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "N1ABC", LocalDateTime.now(), "raw", null);
        packet.setWelcomeCenterId("welcome-center-1");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        String serialized = mapper.writeValueAsString(packet);
        StationPacket deserialized = mapper.readValue(
                "{\"callsign\":\"N1ABC\",\"welcomeCenterId\":\"welcome-center-1\",\"command\":\"raw\"}",
                StationPacket.class);

        assertAll(
                () -> assertEquals("welcome-center-1", packet.getWelcomeCenterId()),
                () -> assertFalse(serialized.contains("welcomeCenterId")),
                () -> assertNull(deserialized.getWelcomeCenterId()),
                () -> assertTrue(StationPacket.class
                        .getDeclaredField("welcomeCenterId")
                        .isAnnotationPresent(jakarta.persistence.Transient.class)));
    }

    @Test
    void weatherReportExposesAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime reportTime = LocalDateTime.of(2026, 8, 22, 10, 15);
        WelcomeCenterWeatherReport report = new WelcomeCenterWeatherReport(
                id, "CHANGEME", "07106.00W", "4218.00N", 72.5f, 61.0f, 29.92f, 850.0f, reportTime);

        assertAll(
                () -> assertEquals(id, report.getId()),
                () -> assertEquals("CHANGEME", report.getCallsign()),
                () -> assertEquals("07106.00W", report.getLongitude()),
                () -> assertEquals("4218.00N", report.getLatitude()),
                () -> assertEquals(72.5f, report.getTemperature()),
                () -> assertEquals(61.0f, report.getHumidity()),
                () -> assertEquals(29.92f, report.getBarometricPressure()),
                () -> assertEquals(850.0f, report.getLuminosity()),
                () -> assertEquals(reportTime, report.getReportTime()));
    }

    @Test
    void weatherSummaryExposesAllFields() {
        UUID welcomeCenterId = UUID.randomUUID();
        LocalDateTime reportTime = LocalDateTime.of(2026, 8, 22, 10, 30);
        WelcomeCenterWeatherSummary summary =
                new WelcomeCenterWeatherSummary(welcomeCenterId, 73.0f, 60.0f, 29.95f, 825.0f, reportTime);

        assertAll(
                () -> assertEquals(welcomeCenterId, summary.getWelcomeCenterId()),
                () -> assertEquals(73.0f, summary.getTemperature()),
                () -> assertEquals(60.0f, summary.getHumidity()),
                () -> assertEquals(29.95f, summary.getBarometricPressure()),
                () -> assertEquals(825.0f, summary.getLuminosity()),
                () -> assertEquals(reportTime, summary.getReportTime()));
    }

    @Test
    void remainingObjectsExposeAllFields() {
        WelcomeCenter c = new WelcomeCenter(
                UUID.randomUUID(), "n", "d", "A", "o", "B", "org", "C", "ct", "D", null, null, "c", "/", List.of());
        CommunicationCategory category = new CommunicationCategory(UUID.randomUUID(), "General", "d", true, false);
        CommunicationPolicy policy = new CommunicationPolicy(
                UUID.randomUUID(),
                category,
                c,
                "hello",
                LocalTime.NOON,
                LocalTime.MIDNIGHT,
                MessageType.MESSAGE,
                CommunicationEventType.ON_REQUEST);
        Announcement a = new Announcement(UUID.randomUUID(), c, policy, null);
        CommunicationEvent e = new CommunicationEvent(
                UUID.randomUUID(), c, CommunicationEventType.ENTER_REGION, a.getId(), null, null);
        StationCommand command =
                new StationCommand(UUID.randomUUID(), "A", c, LocalDateTime.now(), "INFO", StationCommandType.INFO);
        StationPacket packet =
                new StationPacket(UUID.randomUUID(), "processor-1", "A", LocalDateTime.now(), "raw", null);
        StationMessage message = new StationMessage(
                UUID.randomUUID(),
                "A",
                "B",
                c,
                null,
                "hi",
                "processor-1",
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        assertAll(
                () -> assertEquals("General", category.getName()),
                () -> assertTrue(category.isAutoGeneratedText()),
                () -> assertFalse(category.isSystemDefined()),
                () -> assertEquals(MessageType.MESSAGE, policy.getMessageType()),
                () -> assertEquals(CommunicationEventType.ON_REQUEST, policy.getCommunicationEventType()),
                () -> assertNull(a.getSentTime()),
                () -> assertEquals(a.getId(), e.getContext()),
                () -> assertNull(e.getCallsign()),
                () -> assertNull(e.getPacketProcessorId()),
                () -> assertEquals(StationCommandType.INFO, command.getType()),
                () -> assertEquals("processor-1", packet.getPacketProcessorId()),
                () -> assertEquals("raw", packet.getCommand()),
                () -> assertEquals("hi", message.getContent()),
                () -> assertEquals("A", message.getCallsignTo()),
                () -> assertEquals("B", message.getCallsignFrom()),
                () -> assertEquals("processor-1", message.getPacketProcessorId()));
    }
}
