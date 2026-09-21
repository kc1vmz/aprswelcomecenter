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

import com.kc1vmz.aprswc.accessor.CommunicationPolicyAccessor;
import com.kc1vmz.aprswc.accessor.IgnoreStationAccessor;
import com.kc1vmz.aprswc.accessor.StationAccessor;
import com.kc1vmz.aprswc.accessor.StationPacketAccessor;
import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.accessor.WelcomeCenterWeatherReportAccessor;
import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.DistanceUnit;
import com.kc1vmz.aprswc.enumeration.PacketType;
import com.kc1vmz.aprswc.enumeration.StationCommandType;
import com.kc1vmz.aprswc.enumeration.StationState;
import com.kc1vmz.aprswc.object.CommunicationEvent;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationCommand;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherReport;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import com.kc1vmz.aprswc.parser.LocationPacketParser;
import com.kc1vmz.aprswc.parser.MicEPacketParser;
import com.kc1vmz.aprswc.parser.PacketParser;
import com.kc1vmz.aprswc.parser.WeatherPacketParser;
import com.kc1vmz.aprswc.utils.GeoFenceUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StationPacketProcessor {
    private static final Logger objectLog = LoggerFactory.getLogger("ProcessorObjectLog");

    @Autowired
    private StationPacketQueue queue;

    @Autowired
    private StationPacketAccessor stationPacketAccessor;

    @Autowired
    private IgnoreStationAccessor ignoreStationAccessor;

    @Autowired
    private StationAccessor stationAccessor;

    @Autowired
    private PacketParser packetParser;

    @Autowired
    private StationCommandProcessor stationCommandProcessor;

    @Autowired
    private StationCommandQueue stationCommandQueue;

    @Autowired
    private WelcomeCenterAccessor welcomeCenterAccessor;

    @Autowired
    private CommunicationPolicyAccessor communicationPolicyAccessor;

    @Autowired
    private CommunicationEventQueue communicationEventQueue;

    @Autowired
    private WeatherPacketParser weatherPacketParser;

    @Autowired
    private MicEPacketParser micEPacketParser;

    @Autowired
    private LocationPacketParser locationPacketParser;

    @Autowired
    private GeoFenceUtils geoFenceUtils;

    @Autowired
    private StationMessageQueue stationMessageQueue;

    @Autowired
    private WelcomeCenterWeatherReportAccessor welcomeCenterWeatherReportAccessor;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "StationPacketProcessor"));

    @PostConstruct
    void start() {
        worker.submit(this::process);
    }

    private void process() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                StationPacket value = queue.take();
                processPacket(value);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                objectLog.error("StationPacketProcessor failed", exception);
            }
        }
    }

    void processPacket(StationPacket originalPacket) {
        StationPacket packet = new StationPacket(originalPacket);

        PacketType packetType = parsePacket(packet);
        originalPacket.setCallsign(packet.getCallsign());

        if (packetType == PacketType.UNKNOWN) {
            objectLog.debug(
                    "Unknown StationPacket[packetProcessorId={}, callsign={}, receivedTime={}, command={}]",
                    packet.getPacketProcessorId(),
                    packet.getCallsign(),
                    packet.getReceivedTime(),
                    packet.getCommand());
            return;
        }

        if ((isIgnoreStation(packet.getCallsign())) && (packetType != PacketType.MESSAGE)) {
            // ignore it
            return;
        }

        savePacket(originalPacket);

        Station station = null;

        try {
            station = stationAccessor.findByCallsign(packet.getCallsign()).block();
        } catch (Exception e) {
            objectLog.warn("Exception caught", e);
        }
        if (station == null) {
            station = new Station(UUID.randomUUID(), packet.getCallsign(), StationState.UNKNOWN, null);
            stationAccessor.create(station).block();
        }

        switch (packetType) {
            case PacketType.MESSAGE:
                processMessagePacket(packet, station);
                break;
            case PacketType.MICE:
                processMicEPacket(packet, station);
                break;
            case PacketType.WEATHER:
                processWeatherPacket(packet, station);
                break;
            case PacketType.LOCATION:
                WelcomeCenterWeatherReport report = processWeatherPacket(packet, station);
                processLocationPacket(packet, station, report);
                break;
            default:
                break;
        }
    }

    private void processStationPosition(Station station, StationPosition stationPosition, String packetProcessorId) {
        if ((station == null) || (stationPosition == null)) {
            return;
        }

        List<StationPosition> lastPositions = determineLastStationPositions(station);
        lastPositions.addFirst(stationPosition);

        // persist this new packet
        stationAccessor.addPosition(station.getId(), stationPosition).block();

        List<WelcomeCenter> allWelcomeCenters =
                welcomeCenterAccessor.findAll().collectList().block();
        List<WelcomeCenter> welcomeCenters = determineWelcomeCentersEntering(station, lastPositions, allWelcomeCenters);
        List<WelcomeCenter> previousWelcomeCenters =
                determineWelcomeCentersExiting(station, lastPositions, allWelcomeCenters);

        triggerEvents(station, welcomeCenters, previousWelcomeCenters, packetProcessorId);
    }

    private void processLocationPacket(
            StationPacket packet, Station station, WelcomeCenterWeatherReport weatherReport) {
        StationPosition stationPosition = null;
        if ((weatherReport == null)
                || (weatherReport.getLatitude() == null)
                || (weatherReport.getLongitude() == null)) {
            // no position in the weather report
            stationPosition = determineStationPosition(packet, station);
        } else {
            stationPosition = new StationPosition(
                    UUID.randomUUID(),
                    station.getCallsign(),
                    weatherReport.getLongitude(),
                    weatherReport.getLatitude(),
                    weatherReport.getReportTime());
        }
        processStationPosition(station, stationPosition, packet.getPacketProcessorId());
    }

    private void triggerEvents(
            Station station,
            List<WelcomeCenter> currentWelcomeCenters,
            List<WelcomeCenter> previousWelcomeCenters,
            String packetProcessorId) {
        // this is the magic - determine what events to trigger based on what the station is doing
        List<WelcomeCenter> welcomeCentersEntered = new ArrayList<>();
        List<WelcomeCenter> welcomeCentersLeft = new ArrayList<>();

        // determine what was entered and what was left

        // remove from welcome centers left
        for (WelcomeCenter previousWelcomeCenter : previousWelcomeCenters) {
            boolean found = false;
            for (WelcomeCenter currentWelcomeCenter : currentWelcomeCenters) {
                if (previousWelcomeCenter.getId().equals(currentWelcomeCenter.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                welcomeCentersLeft.add(previousWelcomeCenter);
            }
        }
        // add to welcome centers entered
        for (WelcomeCenter currentWelcomeCenter : currentWelcomeCenters) {
            boolean found = false;
            for (WelcomeCenter previousWelcomeCenter : previousWelcomeCenters) {
                if (previousWelcomeCenter.getId().equals(currentWelcomeCenter.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                welcomeCentersEntered.add(currentWelcomeCenter);
            }
        }

        if (!welcomeCentersLeft.isEmpty()) {
            for (WelcomeCenter welcomeCenter : welcomeCentersLeft) {
                // trigger events for each welcome center left
                triggerExitEvents(welcomeCenter, station, packetProcessorId);
            }
        }

        if (!welcomeCentersEntered.isEmpty()) {
            for (WelcomeCenter welcomeCenter : welcomeCentersEntered) {
                // trigger events for each welcome center entered
                triggerEnterEvents(welcomeCenter, station, packetProcessorId);
            }
        }
    }

    void triggerEvents(
            WelcomeCenter welcomeCenter, Station station, CommunicationEventType eventType, String packetProcessorId) {
        if (!welcomeCenter.isOpen()) {
            return;
        }
        // determine the communication policies and execute them
        try {
            List<CommunicationPolicy> policies = communicationPolicyAccessor
                    .findByWelcomeCenterId(welcomeCenter.getId())
                    .collectList()
                    .block();
            if ((policies == null) || (policies.isEmpty())) {
                return;
            }
            for (CommunicationPolicy policy : policies) {
                if (policy.getCommunicationEventType().equals(eventType)) {
                    // trigger this event to occur
                    CommunicationEvent communicationEvent = new CommunicationEvent(
                            UUID.randomUUID(),
                            welcomeCenter,
                            eventType,
                            policy.getId(),
                            station.getCallsign(),
                            packetProcessorId);
                    communicationEventQueue.offer(communicationEvent);
                }
            }
        } catch (Exception e) {
            objectLog.error("Exception caught triggering welcome center enter events", e);
        }
    }

    void triggerEnterEvents(WelcomeCenter welcomeCenter, Station station, String packetProcessorId) {
        triggerEvents(welcomeCenter, station, CommunicationEventType.ENTER_REGION, packetProcessorId);
    }

    void triggerExitEvents(WelcomeCenter welcomeCenter, Station station, String packetProcessorId) {
        triggerEvents(welcomeCenter, station, CommunicationEventType.EXIT_REGION, packetProcessorId);
    }

    private List<WelcomeCenter> determineWelcomeCentersEntering(
            Station station, List<StationPosition> stationPositions, List<WelcomeCenter> welcomeCenters) {
        List<WelcomeCenter> ret = new ArrayList<>();
        if ((welcomeCenters == null) || (stationPositions == null)) {
            return ret;
        }
        if (stationPositions.size() < 4) {
            // need three in plus one not
            return ret;
        }
        for (WelcomeCenter welcomeCenter : welcomeCenters) {
            // for each welcome center, determine if position is in one of its regions
            List<WelcomeRegion> regions = welcomeCenter.getRegions();
            if (regions == null) {
                continue;
            }
            for (WelcomeRegion region : regions) {
                StationPosition position3 = stationPositions.get(3);
                StationPosition position2 = stationPositions.get(2);
                StationPosition position1 = stationPositions.get(1);
                StationPosition position0 = stationPositions.get(0);

                if ((geoFenceUtils.isInGeoFenceRegion(region, position0))
                        && (geoFenceUtils.isInGeoFenceRegion(region, position1))
                        && (geoFenceUtils.isInGeoFenceRegion(region, position2))
                        && (!geoFenceUtils.isInGeoFenceRegion(region, position3))) {
                    ret.add(welcomeCenter);
                    break;
                }
            }
        }
        return ret;
    }

    private List<WelcomeCenter> determineWelcomeCentersExiting(
            Station station, List<StationPosition> stationPositions, List<WelcomeCenter> welcomeCenters) {
        List<WelcomeCenter> ret = new ArrayList<>();
        if ((welcomeCenters == null) || (stationPositions == null)) {
            return ret;
        }
        if (stationPositions.size() < 4) {
            // need 4 points at least
            return ret;
        }

        for (WelcomeCenter welcomeCenter : welcomeCenters) {
            // for each welcome center, determine if position is in one of its regions
            List<WelcomeRegion> regions = welcomeCenter.getRegions();
            if (regions == null) {
                continue;
            }
            for (WelcomeRegion region : regions) {
                StationPosition position0 = stationPositions.get(0);
                StationPosition position1 = stationPositions.get(1);
                StationPosition position2 = stationPositions.get(2);
                StationPosition position3 = stationPositions.get(3);

                // last 3 positions out, 4 still in
                if ((!geoFenceUtils.isInGeoFenceRegion(region, position0))
                        && (!geoFenceUtils.isInGeoFenceRegion(region, position1))
                        && (!geoFenceUtils.isInGeoFenceRegion(region, position2))
                        && (geoFenceUtils.isInGeoFenceRegion(region, position3))) {
                    // elvis has left the building
                    ret.add(welcomeCenter);
                    break;
                }
            }
        }
        return ret;
    }

    private void savePacket(StationPacket packet) {
        stationPacketAccessor.save(packet).block();
    }

    boolean essentiallyStopped(StationPosition positionFirst, StationPosition positionSecond) {
        if ((positionFirst == null) || (positionSecond == null)) {
            return true;
        }
        // are these points within 50 feet of each other
        return geoFenceUtils.isInGeofenceCircle(
                positionFirst.getLongitude(), positionFirst.getLatitude(), 50, DistanceUnit.FEET, positionSecond);
    }

    List<StationPosition> determineLastStationPositions(Station station) {
        return stationAccessor.findPositions(station.getId(), 5).collectList().block();
    }

    StationPosition determineStationPosition(StationPacket packet, Station station) {
        return locationPacketParser.parseLocationPacket(packet, station);
    }

    public WelcomeCenterWeatherReport processWeatherPacket(StationPacket packet, Station station) {
        WelcomeCenterWeatherReport report = weatherPacketParser.parseWeatherPacket(packet);
        if (report != null) {
            welcomeCenterWeatherReportAccessor.create(report).block();
        }
        return report;
    }

    void processMicEPacket(StationPacket packet, Station station) {
        StationPosition stationPosition = micEPacketParser.parseMicEPacket(packet, station);
        processStationPosition(station, stationPosition, packet.getPacketProcessorId());
    }

    private void processMessagePacket(StationPacket packet, Station station) {
        determineCallsignTo(packet);
        if (welcomeCenterAccessor.findOpenByCallsign(packet.getCallsignTo()).block() == null) {
            // not ours
            return;
        }
        // handle ack and rej
        if (isAckOrRej(packet)) {
            // ignore packet - we are not retrying
            return;
        }
        if (isWelcomeCenterCommand(packet)) {
            ackPacket(packet);
            processWelcomeCenterCommand(packet);
        } else {
            rejectPacket(packet);
        }
    }

    private void rejectPacket(StationPacket packet) {
        String ackId = getPacketAckId(packet);
        if (ackId != null) {
            StationMessage stationMessage = new StationMessage(
                    UUID.randomUUID(),
                    packet.getCallsign(),
                    packet.getCallsignTo(),
                    null,
                    null,
                    "rej" + ackId,
                    packet.getPacketProcessorId(),
                    com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
            stationMessageQueue.offer(stationMessage);
        }
    }

    private void ackPacket(StationPacket packet) {
        String ackId = getPacketAckId(packet);
        if (ackId != null) {
            StationMessage stationMessage = new StationMessage(
                    UUID.randomUUID(),
                    packet.getCallsign(),
                    packet.getCallsignTo(),
                    null,
                    null,
                    "ack" + ackId,
                    packet.getPacketProcessorId(),
                    com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
            stationMessageQueue.offer(stationMessage);
        }
    }

    private String getPacketAckId(StationPacket packet) {
        String packetId = null;
        String content = packet.getCommand();
        if ((content == null) || (content.isBlank()) || (content.isEmpty())) {
            return packetId;
        }
        try {
            int indexAckPos = content.indexOf("{");
            if (indexAckPos != -1) {
                packetId = content.substring(indexAckPos + 1);
                int index = packetId.indexOf("}");
                if (index != -1) {
                    packetId = packetId.substring(0, index); // handle {XXXX}YYYY
                }
            }
        } catch (Exception e) {
            objectLog.error("Exception caught parsing ack id", e);
            packetId = null;
        }

        return packetId;
    }

    void processWelcomeCenterCommand(StationPacket packet) {
        StationCommandType type = StationCommandType.UNKNOWN; // will get filled in later
        WelcomeCenter welcomeCenter =
                welcomeCenterAccessor.findOpenByCallsign(packet.getCallsignTo()).block();
        if (welcomeCenter == null) return;
        StationCommand command = new StationCommand(
                UUID.randomUUID(),
                packet.getCallsign(),
                welcomeCenter,
                packet.getReceivedTime(),
                packet.getCommand(),
                type);
        command.setPacketProcessorId(packet.getPacketProcessorId());
        stationCommandQueue.offer(command);
    }

    private void determineCallsignTo(StationPacket packet) {
        String content = packet.getCommand();
        if ((content == null) || (content.isBlank()) || (content.isEmpty())) {
            return;
        }
        content = content.substring(1); // skip over message DTI character

        try {
            int indexToSep = content.indexOf(":");
            if (indexToSep == -1) {
                objectLog.error("Could not find callsignTo separator - " + content);
                return;
            }
            String callsignTo = content.substring(0, indexToSep);
            packet.setCallsignTo(callsignTo.stripTrailing().toUpperCase());
            content = content.substring(indexToSep + 1);
            packet.setCommand(content);
        } catch (Exception e) {
            objectLog.error("Exception caught parsing message", e);
            return;
        }
    }

    private boolean isWelcomeCenterCommand(StationPacket packet) {
        String content = packet.getCommand();
        if ((content == null) || (content.isBlank()) || (content.isEmpty())) {
            return false;
        }
        return stationCommandProcessor.isRecognizedCommand(packet.getCommand());
    }

    private boolean isIgnoreStation(String callsign) {
        return ignoreStationAccessor.isIgnoreStation(callsign);
    }

    private boolean isAckOrRej(StationPacket packet) {
        String content = packet.getCommand();
        if ((content == null) || (content.isBlank()) || (content.isEmpty())) {
            return false;
        }
        if (content.startsWith("ack") || content.startsWith("rej")) {
            return true;
        }
        return false;
    }

    private PacketType parsePacket(StationPacket packet) {
        return packetParser.parse(packet);
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
