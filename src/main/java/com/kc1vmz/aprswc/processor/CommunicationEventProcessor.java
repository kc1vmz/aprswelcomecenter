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
import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.CommunicationEvent;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import com.kc1vmz.aprswc.object.StationMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommunicationEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(CommunicationEventProcessor.class);

    @Autowired
    private CommunicationEventQueue queue;

    @Autowired
    private CommunicationPolicyAccessor communicationPolicyAccessor;

    @Autowired
    private StationMessageQueue stationMessageQueue;

    @Autowired
    private com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor welcomeCenterAccessor;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "CommunicationEventProcessor"));

    @PostConstruct
    void start() {
        worker.submit(this::process);
    }

    private void process() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                CommunicationEvent value = queue.take();
                processEvent(value);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                log.error("CommunicationEventProcessor failed", exception);
            }
        }
    }

    void processEvent(CommunicationEvent event) {
        if ((event.getType().equals(CommunicationEventType.ENTER_REGION))
                || (event.getType().equals(CommunicationEventType.EXIT_REGION))) {
            CommunicationPolicy policy =
                    communicationPolicyAccessor.findById(event.getContext()).block();
            processCommunicationPolicy(event, policy);
        }
    }

    void processCommunicationPolicy(CommunicationEvent event, CommunicationPolicy policy) {
        // need to send a StationMessage based on the
        if (policy == null || policy.getWelcomeCenter() == null) {
            return;
        }
        var center = welcomeCenterAccessor
                .findOpenById(policy.getWelcomeCenter().getId())
                .block();
        if (center == null) return;
        if (policy.getMessageType().equals(MessageType.MESSAGE)) {
            StationMessage stationMessage = new StationMessage(
                    UUID.randomUUID(),
                    event.getCallsign(),
                    center.getCallsign(),
                    center,
                    null,
                    policy.getMessageText(),
                    null,
                    MessageType.MESSAGE);
            stationMessage.setPacketProcessorId(event.getPacketProcessorId());
            stationMessage.setRequiresOpenCenter(true);
            stationMessageQueue.offer(stationMessage);
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
