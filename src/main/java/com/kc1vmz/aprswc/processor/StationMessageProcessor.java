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

import com.kc1vmz.aprswc.accessor.StationAccessor;
import com.kc1vmz.aprswc.accessor.StationMessageAccessor;
import com.kc1vmz.aprswc.accessor.StationPacketAccessor;
import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.communication.CommunicationInstanceManager;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.Station;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.StationPacket;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StationMessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(StationMessageProcessor.class);

    @Autowired
    private StationMessageQueue queue;

    @Autowired
    private WelcomeCenterAccessor welcomeCenterAccessor;

    @Autowired
    private StationAccessor stationAccessor;

    @Autowired
    private StationMessageAccessor stationMessageAccessor;

    @Autowired
    private StationPacketAccessor stationPacketAccessor;

    @Autowired
    private CommunicationInstanceManager communications;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "StationMessageProcessor"));

    @PostConstruct
    void start() {
        worker.submit(this::process);
    }

    private void process() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                StationMessage value = queue.take();
                processMessage(value);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                log.error("StationMessageProcessor failed", exception);
            }
        }
    }

    void processMessage(StationMessage message) {
        if (message == null) {
            log.error("Invalid station message");
            return;
        }

        if (message.isRequiresOpenCenter()
                && (message.getWelcomeCenter() == null
                        || welcomeCenterAccessor
                                        .findOpenById(message.getWelcomeCenter().getId())
                                        .block()
                                == null)) {
            return;
        }
        if (message.getMessageType().equals(MessageType.MESSAGE)) {
            if (message.getCallsignTo() != null) {
                // send directed single message
                sendDirectedMessage(message);
                return;
            }
            if (message.getWelcomeCenter() != null) {
                // send to all in welcome center
                sendWelcomeCenterMessage(message);
            }
        } else if (message.getMessageType().equals(MessageType.BULLETIN)) {
            if (message.getCallsignTo() != null) {
                // send bulletin
                sendBulletinMessage(message);
                return;
            }
        }
    }

    private void sendBulletinMessage(StationMessage message) {
        if (message.getCallsignFrom() == null) {
            log.error("No callsignFrom in StationMessage");
            return;
        }

        communications.sendBulletin(message.getCallsignFrom(), message.getCallsignTo(), message.getContent());
    }

    private void sendWelcomeCenterMessage(StationMessage message) {
        // given the welcome center get the stations within it
        List<Station> stations = welcomeCenterAccessor
                .findStationPositions(message.getWelcomeCenter().getId())
                .map(position -> position.getStationId())
                .filter(stationId -> stationId != null)
                .distinct()
                .flatMap(stationAccessor::findById)
                .collectList()
                .block();
        if ((stations == null) || (stations.isEmpty())) {
            return;
        }

        String callsignFrom = message.getCallsignFrom();
        if ((callsignFrom == null) || (callsignFrom.isEmpty())) {
            callsignFrom = message.getWelcomeCenter().getCallsign();
        }
        for (Station station : stations) {
            try {
                StationMessage specificMessage = new StationMessage(
                        UUID.randomUUID(),
                        station.getCallsign(),
                        callsignFrom,
                        message.getWelcomeCenter(),
                        null,
                        message.getContent(),
                        null,
                        com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
                stationMessageAccessor.create(specificMessage).block();
            } catch (Exception e) {
                log.error("Exception caught sending welcome center message to station", e);
            }
        }
    }

    private void sendDirectedMessage(StationMessage message) {
        if (message.getCallsignFrom() == null) {
            log.error("No callsignFrom in StationMessage");
            return;
        }
        if (message.getPacketProcessorId() == null
                || message.getPacketProcessorId().isBlank())
            message.setPacketProcessorId(determinePacketProcessor(message));
        boolean queued = communications.sendMessage(
                message.getPacketProcessorId(),
                message.getCallsignFrom(),
                message.getCallsignTo(),
                message.getContent(),
                () -> !message.isRequiresOpenCenter()
                        || (message.getWelcomeCenter() != null
                                && welcomeCenterAccessor
                                                .findOpenById(message.getWelcomeCenter()
                                                        .getId())
                                                .block()
                                        != null),
                () -> {
                    message.setSentTime(LocalDateTime.now());
                    stationMessageAccessor.saveProcessedMessage(message).block();
                });
        if (!queued)
            log.debug("Dropping message with unavailable communication route {}", message.getPacketProcessorId());
    }

    private String determinePacketProcessor(StationMessage message) {
        String ret = "";

        List<StationPacket> stationPackets = stationPacketAccessor
                .findAllByCallsign(message.getCallsignTo())
                .collectList()
                .block();
        if ((stationPackets == null) || (stationPackets.isEmpty())) {
            return "";
        }
        Set<String> uniquePacketProcessorIds = new HashSet<>();
        for (StationPacket stationPacket : stationPackets) {
            uniquePacketProcessorIds.add(stationPacket.getPacketProcessorId());
        }
        switch (uniquePacketProcessorIds.size()) {
            case 0:
                // none to choose from
                break;
            case 1:
                ret = uniquePacketProcessorIds.iterator().next();
                break;
            default:
                // need to choose between the options, favor last
                ret = stationPackets.get(0).getPacketProcessorId();
                break;
        }

        return ret;
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
