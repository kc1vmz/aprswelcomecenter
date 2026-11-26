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
package com.kc1vmz.aprswc.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class AprsCoordinateFormat {
    private AprsCoordinateFormat() {}

    public static String latitude(double value) {
        return Double.isFinite(value) ? format(BigDecimal.valueOf(value), 2, 'N', 'S', 90) : null;
    }

    public static String longitude(double value) {
        return Double.isFinite(value) ? format(BigDecimal.valueOf(value), 3, 'E', 'W', 180) : null;
    }

    public static String format(
            BigDecimal coordinate, int degreeWidth, char positiveDirection, char negativeDirection, int maximum) {
        if (coordinate == null || coordinate.abs().compareTo(BigDecimal.valueOf(maximum)) > 0) {
            return null;
        }

        BigDecimal absolute = coordinate.abs();
        int degrees = absolute.intValue();
        BigDecimal minutes = absolute.subtract(BigDecimal.valueOf(degrees))
                .multiply(BigDecimal.valueOf(60))
                .setScale(2, RoundingMode.HALF_UP);
        if (minutes.compareTo(BigDecimal.valueOf(60)) == 0) {
            degrees += 1;
            minutes = BigDecimal.ZERO.setScale(2);
        }
        if (degrees > maximum) {
            return null;
        }

        char direction = coordinate.signum() < 0 ? negativeDirection : positiveDirection;
        return String.format(Locale.ROOT, "%0" + degreeWidth + "d%05.2f%c", degrees, minutes.doubleValue(), direction);
    }
}
