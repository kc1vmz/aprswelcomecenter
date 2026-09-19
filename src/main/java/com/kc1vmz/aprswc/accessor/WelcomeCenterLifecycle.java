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
package com.kc1vmz.aprswc.accessor;

import com.kc1vmz.aprswc.enumeration.MessageType;
import com.kc1vmz.aprswc.enumeration.WelcomeCenterStatus;
import com.kc1vmz.aprswc.object.*;
import com.kc1vmz.aprswc.processor.ObjectBeaconQueue;
import com.kc1vmz.aprswc.processor.StationMessageQueue;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WelcomeCenterLifecycle {
    private static final Logger log = LoggerFactory.getLogger(WelcomeCenterLifecycle.class);

    @Autowired
    private ObjectBeaconQueue objectBeaconQueue;

    @Autowired
    private StationMessageQueue stationMessageQueue;

    @TransactionalEventListener
    public void afterCommit(WelcomeCenterChanged event) {
        var previous = event.previous();
        var updated = event.updated();
        try {
            if (previous.status() != updated.status()) {
                if (updated.status() == WelcomeCenterStatus.CLOSED) onWelcomeCenterClosed(previous, updated);
                else onWelcomeCenterOpened(previous, updated);
            } else if (updated.status() == WelcomeCenterStatus.OPEN
                    && !previous.callsign().equalsIgnoreCase(updated.callsign())) {
                beacon(previous, false);
                beacon(updated, true);
            }
        } catch (RuntimeException failure) {
            // The database edit already committed. Do not report it as a failed save or repeat the transition.
            log.error("Welcome Center {} saved, but its lifecycle action failed", updated.id(), failure);
        }
    }

    public void onWelcomeCenterClosed(WelcomeCenterSnapshot previous, WelcomeCenterSnapshot updated) {
        beacon(updated, false);
        announceClosed(updated);
    }

    public void onWelcomeCenterOpened(WelcomeCenterSnapshot previous, WelcomeCenterSnapshot updated) {
        beacon(updated, true);
        announceOpen(updated);
    }

    private void beacon(WelcomeCenterSnapshot welcomeCenterSnapshot, boolean active) {
        if (welcomeCenterSnapshot.longitude() == null || welcomeCenterSnapshot.latitude() == null) {
            return;
        }

        objectBeaconQueue.offer(new ObjectBeacon(
                welcomeCenterSnapshot.callsign(),
                welcomeCenterSnapshot.ownerCallsign(),
                welcomeCenterSnapshot.longitude(),
                welcomeCenterSnapshot.latitude(),
                welcomeCenterSnapshot.symbolCode(),
                welcomeCenterSnapshot.symbolId(),
                "APRS Welcome Center",
                active));
    }

    private void announceOpen(WelcomeCenterSnapshot welcomeCenterSnapshot) {
        String bulletinContent = String.format(
                "APRS Welcome Center '%s' now open - send HELP for more commands.", welcomeCenterSnapshot.callsign());
        bulletin(welcomeCenterSnapshot, bulletinContent);
    }

    private void announceClosed(WelcomeCenterSnapshot welcomeCenterSnapshot) {
        String bulletinContent =
                String.format("APRS Welcome Center '%s' now closed.", welcomeCenterSnapshot.callsign());
        bulletin(welcomeCenterSnapshot, bulletinContent);
    }

    private void bulletin(WelcomeCenterSnapshot welcomeCenterSnapshot, String bulletinContent) {
        StationMessage stationMessage = new StationMessage(
                UUID.randomUUID(),
                "BLN0",
                welcomeCenterSnapshot.callsign(),
                new WelcomeCenter(
                        welcomeCenterSnapshot.id(),
                        welcomeCenterSnapshot.name(),
                        null,
                        welcomeCenterSnapshot.callsign(),
                        null,
                        welcomeCenterSnapshot.ownerCallsign(),
                        null,
                        null,
                        null,
                        null,
                        welcomeCenterSnapshot.longitude(),
                        welcomeCenterSnapshot.latitude(),
                        welcomeCenterSnapshot.symbolCode(),
                        welcomeCenterSnapshot.symbolId(),
                        null),
                LocalDateTime.now(),
                bulletinContent,
                null,
                MessageType.BULLETIN);
        stationMessageQueue.offer(stationMessage);
    }
}
