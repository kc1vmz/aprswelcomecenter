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

import com.kc1vmz.aprswc.object.CoordinatePair;
import com.kc1vmz.aprswc.object.DecimalCoordinate;
import com.kc1vmz.aprswc.object.DecimalCoordinatePair;
import com.kc1vmz.aprswc.utils.AprsCoordinateFormat;
import com.kc1vmz.aprswc.utils.ConvertLonLat;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CoordinateAccessor {
    public Mono<List<DecimalCoordinate>> convert(List<CoordinatePair> coordinates) {
        return Mono.just(coordinates.stream().map(this::convertCoordinate).toList());
    }

    public DecimalCoordinate convertCoordinate(CoordinatePair coordinate) {
        double longitude = ConvertLonLat.convertLongitude(coordinate.getLongitude());
        double latitude = ConvertLonLat.convertLatitude(coordinate.getLatitude());
        boolean valid = true;

        if ((longitude == ConvertLonLat.INVALID_VALUE) || (latitude == ConvertLonLat.INVALID_VALUE)) {
            valid = false;
        }
        return new DecimalCoordinate(
                BigDecimal.valueOf(longitude), BigDecimal.valueOf(latitude), valid, "Bad coordinates");
    }

    public Mono<List<CoordinatePair>> convertToAprs(List<DecimalCoordinatePair> coordinates) {
        return Mono.just(coordinates.stream().map(this::convertToAprsCoordinate).toList());
    }

    public CoordinatePair convertToAprsCoordinate(DecimalCoordinatePair coordinate) {
        return new CoordinatePair(
                AprsCoordinateFormat.format(coordinate.getLongitude(), 3, 'E', 'W', 180),
                AprsCoordinateFormat.format(coordinate.getLatitude(), 2, 'N', 'S', 90));
    }
}
