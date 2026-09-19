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
import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.accessor.WelcomeCenterWeatherReportAccessor;
import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import com.kc1vmz.aprswc.object.StationCommand;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherSummary;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StationCommandProcessor {
    private static final Logger log = LoggerFactory.getLogger(StationCommandProcessor.class);
    private static final Logger objectLog = LoggerFactory.getLogger("ProcessorObjectLog");
    private static final String INFO_COMMAND = "INFO";
    private static final String WEATHER_COMMAND = "WEATHER";
    private static final String VOICE_COMMAND = "VOICE";
    private static final String COMM_COMMAND = "COMM";
    private static final String CLUBS_COMMAND = "CLUBS";
    private static final String HELP_COMMAND = "HELP";
    private static final String EVENTS_COMMAND = "EVENTS";
    private static final String WARNINGS_COMMAND = "WARNINGS";
    private static final String STOP_COMMAND = "STOP";
    private static final String START_COMMAND = "START";
    private static final String OTHERS_COMMAND = "OTHERS";
    private static final Set<String> WELCOME_CENTER_COMMANDS = Set.of(
            INFO_COMMAND,
            STOP_COMMAND,
            START_COMMAND,
            WEATHER_COMMAND,
            VOICE_COMMAND,
            COMM_COMMAND,
            OTHERS_COMMAND,
            CLUBS_COMMAND,
            EVENTS_COMMAND,
            WARNINGS_COMMAND,
            HELP_COMMAND);

    @Autowired
    private StationCommandQueue queue;

    @Autowired
    private StationMessageQueue stationMessageQueue;

    @Autowired
    private WelcomeCenterWeatherReportAccessor welcomeCenterWeatherReportAccessor;

    @Autowired
    private WelcomeCenterAccessor welcomeCenterAccessor;

    @Autowired
    private IgnoreStationAccessor ignoreStationAccessor;

    @Autowired
    private CommunicationPolicyAccessor communicationPolicyAccessor;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "StationCommandProcessor"));

    @PostConstruct
    void start() {
        worker.submit(this::process);
    }

    private void process() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                StationCommand value = queue.take();
                processCommand(value);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                log.error("StationCommandProcessor failed", exception);
            }
        }
    }

    void processCommand(StationCommand command) {
        if ((command == null) || (command.getCommand() == null)) {
            return;
        }
        if (command.getWelcomeCenter() == null) return;
        WelcomeCenter current = welcomeCenterAccessor
                .findOpenById(command.getWelcomeCenter().getId())
                .block();
        if (current == null) return;
        command.setWelcomeCenter(current);
        String actualCommand = recognizedCommand(command.getCommand());
        if (actualCommand == null) {
            return;
        }
        if (actualCommand.equalsIgnoreCase(HELP_COMMAND)) {
            processHelpRequest(command);
        } else if (actualCommand.equalsIgnoreCase(INFO_COMMAND)) {
            processInfoRequest(command);
        } else if (actualCommand.equalsIgnoreCase(WEATHER_COMMAND)) {
            processWeatherRequest(command);
        } else if (actualCommand.equalsIgnoreCase(STOP_COMMAND)) {
            processStopRequest(command);
        } else if (actualCommand.equalsIgnoreCase(START_COMMAND)) {
            processStartRequest(command);
        } else if (actualCommand.equalsIgnoreCase(OTHERS_COMMAND)) {
            processOthersRequest(command);
        } else if (actualCommand.equalsIgnoreCase(VOICE_COMMAND)) {
            processVoiceRequest(command);
        } else if (actualCommand.equalsIgnoreCase(COMM_COMMAND)) {
            processCommRequest(command);
        } else if (actualCommand.equalsIgnoreCase(CLUBS_COMMAND)) {
            processClubsRequest(command);
        } else if (actualCommand.equalsIgnoreCase(EVENTS_COMMAND)) {
            processEventsRequest(command);
        } else if (actualCommand.equalsIgnoreCase(WARNINGS_COMMAND)) {
            processWarningsRequest(command);
        }
    }

    private void queueReply(StationMessage message) {
        message.setRequiresOpenCenter(true);
        stationMessageQueue.offer(message);
    }

    private void processVoiceRequest(StationCommand command) {
        processCommunicationPolicyRequest(command, "Amateur Radio");
    }

    private void processCommRequest(StationCommand command) {
        processCommunicationPolicyRequest(command, "Commercial Radio");
    }

    private void processClubsRequest(StationCommand command) {
        processCommunicationPolicyRequest(command, "Clubs");
    }

    private void processEventsRequest(StationCommand command) {
        processCommunicationPolicyRequest(command, "Events");
    }

    private void processWarningsRequest(StationCommand command) {
        processCommunicationPolicyRequest(command, "Warnings");
    }

    private void processCommunicationPolicyRequest(StationCommand command, String name) {
        List<CommunicationPolicy> communicationPolicies = getCommunicationPolicies(command.getWelcomeCenter(), name);
        if ((communicationPolicies == null) || (communicationPolicies.isEmpty())) {
            CommunicationPolicy policy = new CommunicationPolicy(
                    UUID.randomUUID(),
                    null,
                    command.getWelcomeCenter(),
                    name + ": No information available",
                    null,
                    null,
                    MessageType.MESSAGE,
                    null);
            communicationPolicies.add(policy);
        }
        for (CommunicationPolicy communicationPolicy : communicationPolicies) {
            String messageText = communicationPolicy.getMessageText();
            StationMessage stationMessage = new StationMessage(
                    UUID.randomUUID(),
                    command.getCallsign(),
                    command.getWelcomeCenter().getCallsign(),
                    command.getWelcomeCenter(),
                    null,
                    messageText,
                    null,
                    com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
            queueReply(stationMessage);
        }
    }

    private List<CommunicationPolicy> getCommunicationPolicies(WelcomeCenter welcomeCenter, String name) {
        if ((welcomeCenter == null) || (welcomeCenter.getId() == null) || (name == null)) {
            return List.of();
        }
        return communicationPolicyAccessor
                .findByWelcomeCenterId(welcomeCenter.getId())
                .filter(policy -> policy.getCategory() != null)
                .filter(policy -> policy.getCategory().getName() != null)
                .filter(policy -> policy.getCategory().getName().equalsIgnoreCase(name))
                .collectList()
                .blockOptional()
                .orElseGet(List::of);
    }

    private void processOthersRequest(StationCommand command) {
        List<String> messages = getOthers(command);

        if ((messages == null) || (messages.isEmpty())) {
            String messageText = String.format("No others known to this welcome center");
            StationMessage stationMessage = new StationMessage(
                    UUID.randomUUID(),
                    command.getCallsign(),
                    command.getWelcomeCenter().getCallsign(),
                    command.getWelcomeCenter(),
                    null,
                    messageText,
                    null,
                    com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
            queueReply(stationMessage);
        } else {
            for (String messageText : messages) {
                StationMessage stationMessage = new StationMessage(
                        UUID.randomUUID(),
                        command.getCallsign(),
                        command.getWelcomeCenter().getCallsign(),
                        command.getWelcomeCenter(),
                        null,
                        messageText,
                        null,
                        com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
                queueReply(stationMessage);
            }
        }
    }

    private List<String> getOthers(StationCommand command) {
        List<String> callsigns = getOthersCallsigns(command);
        List<String> messages = new ArrayList<>();
        List<String> aggregatedCallsigns = new ArrayList<>();
        for (int index = 0; index < callsigns.size(); index += 6) {
            aggregatedCallsigns.add(String.join(",", callsigns.subList(index, Math.min(index + 6, callsigns.size()))));
        }

        int index = 1;
        for (String aggregatedCallsign : aggregatedCallsigns) {
            messages.add(String.format("Others (%d/%d) %s", index, aggregatedCallsigns.size(), aggregatedCallsign));
            index++;
        }

        return messages;
    }

    private List<String> getOthersCallsigns(StationCommand command) {
        List<String> callsigns = welcomeCenterAccessor
                .findStationPositions(command.getWelcomeCenter().getId())
                .map(stationPosition -> stationPosition.getCallsign())
                .filter(callsign -> (callsign != null) && !callsign.isBlank())
                .filter(callsign ->
                        (command.getCallsign() == null) || !callsign.equalsIgnoreCase(command.getCallsign()))
                .distinct()
                .collectList()
                .block();
        if ((callsigns == null) || callsigns.isEmpty()) {
            return List.of();
        }
        return callsigns;
    }

    private void processStartRequest(StationCommand command) {
        String welcomeCenterCallsigns = getAllWelcomeCenterCallsigns();

        ignoreStationAccessor.deleteByCallsign(command.getCallsign()).block();

        String messageText = String.format("Resuming messages from %s", welcomeCenterCallsigns);
        StationMessage stationMessage = new StationMessage(
                UUID.randomUUID(),
                command.getCallsign(),
                command.getWelcomeCenter().getCallsign(),
                command.getWelcomeCenter(),
                null,
                messageText,
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        queueReply(stationMessage);

        String messageText2 = "Send STOP to stop receiving messages";
        StationMessage stationMessage2 = new StationMessage(
                UUID.randomUUID(),
                command.getCallsign(),
                command.getWelcomeCenter().getCallsign(),
                command.getWelcomeCenter(),
                null,
                messageText2,
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        queueReply(stationMessage2);
    }

    private void processStopRequest(StationCommand command) {
        String welcomeCenterCallsigns = getAllWelcomeCenterCallsigns();

        ignoreStationAccessor.createIfAbsent(command.getCallsign()).block();
        String messageText = String.format("Stopping messages from %s", welcomeCenterCallsigns);
        StationMessage stationMessage = new StationMessage(
                UUID.randomUUID(),
                command.getCallsign(),
                command.getWelcomeCenter().getCallsign(),
                command.getWelcomeCenter(),
                null,
                messageText,
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        queueReply(stationMessage);

        String messageText2 = "Send START to resume receiving messages";
        StationMessage stationMessage2 = new StationMessage(
                UUID.randomUUID(),
                command.getCallsign(),
                command.getWelcomeCenter().getCallsign(),
                command.getWelcomeCenter(),
                null,
                messageText2,
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        queueReply(stationMessage2);
    }

    private String getAllWelcomeCenterCallsigns() {
        return welcomeCenterAccessor
                .findAll()
                .map(welcomeCenter -> welcomeCenter.getCallsign())
                .filter(callsign -> (callsign != null) && !callsign.isBlank())
                .collect(Collectors.joining(","))
                .blockOptional()
                .orElse("");
    }

    private void processWeatherRequest(StationCommand command) {
        WelcomeCenterWeatherSummary summary = welcomeCenterWeatherReportAccessor
                .findSummary(command.getWelcomeCenter().getId())
                .block();
        String messageText = "";
        if (summary == null) {
            messageText = "No current weather summary available";
        } else {
            messageText = String.format(
                    Locale.ROOT,
                    "Temp: %.0fF Hum: %.0f%% Bar: %.0fMb Lum: %.0f",
                    summary.getTemperature(),
                    summary.getHumidity(),
                    summary.getBarometricPressure(),
                    summary.getLuminosity());
        }

        StationMessage stationMessage = new StationMessage(
                UUID.randomUUID(),
                command.getCallsign(),
                command.getWelcomeCenter().getCallsign(),
                command.getWelcomeCenter(),
                null,
                messageText,
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        queueReply(stationMessage);
    }

    private void processInfoRequest(StationCommand command) {
        // send three messages
        String messageText = String.format(
                "%s handled by APRS Welcome Center '%s'",
                command.getWelcomeCenter().getCallsign(),
                command.getWelcomeCenter().getName());
        String messageText2 = String.format(
                "APRS Welcome Center '%s' managed by %s",
                command.getWelcomeCenter().getName(),
                (command.getWelcomeCenter().getOwnerCallsign() == null)
                        ? "UNKNOWN"
                        : command.getWelcomeCenter().getOwnerCallsign());

        StationMessage stationMessage = new StationMessage(
                UUID.randomUUID(),
                command.getCallsign(),
                command.getWelcomeCenter().getCallsign(),
                command.getWelcomeCenter(),
                null,
                messageText,
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        queueReply(stationMessage);
        StationMessage stationMessage2 = new StationMessage(
                UUID.randomUUID(),
                command.getCallsign(),
                command.getWelcomeCenter().getCallsign(),
                command.getWelcomeCenter(),
                null,
                messageText2,
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);
        queueReply(stationMessage2);
        processHelpRequest(command);
    }

    private void processHelpRequest(StationCommand command) {
        String messageText = "Commands: HELP,INFO,WEATHER,START,STOP,OTHERS,COMM,VOICE,CLUBS,EVENTS,WARNINGS";

        StationMessage stationMessage = new StationMessage(
                UUID.randomUUID(),
                command.getCallsign(),
                command.getWelcomeCenter().getCallsign(),
                command.getWelcomeCenter(),
                null,
                messageText,
                null,
                com.kc1vmz.aprswc.enumeration.MessageType.MESSAGE);

        queueReply(stationMessage);
    }

    public boolean isRecognizedCommand(String content) {
        return recognizedCommand(content) != null;
    }

    private String recognizedCommand(String content) {
        String command = null;

        if (content == null) {
            return null;
        }

        try {
            int indexAck = content.indexOf("{");
            if (indexAck != -1) {
                content = content.substring(0, indexAck);
            }
            String[] commands = content.split("\\\\s+");
            if ((commands != null) && (commands.length > 0)) {
                command = commands[0];
            }
        } catch (Exception e) {
            objectLog.error("Exception caught parsing message for command", e);
        }

        if ((command != null)
                && (WELCOME_CENTER_COMMANDS.contains(command.trim().toUpperCase(Locale.ROOT)))) {
            return command;
        }
        return null;
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
