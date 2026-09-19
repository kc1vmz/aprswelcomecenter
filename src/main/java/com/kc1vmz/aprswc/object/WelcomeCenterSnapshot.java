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

import com.kc1vmz.aprswc.enumeration.WelcomeCenterStatus;
import java.util.UUID;

/** Immutable values captured before and after a committed edit. */
public record WelcomeCenterSnapshot(
        UUID id,
        String name,
        String callsign,
        String ownerCallsign,
        String longitude,
        String latitude,
        String symbolCode,
        String symbolId,
        WelcomeCenterStatus status) {
    public static WelcomeCenterSnapshot of(WelcomeCenter center) {
        return new WelcomeCenterSnapshot(
                center.getId(),
                center.getName(),
                center.getCallsign(),
                center.getOwnerCallsign(),
                center.getLongitude(),
                center.getLatitude(),
                center.getSymbolCode(),
                center.getSymbolId(),
                center.getStatus());
    }
}
