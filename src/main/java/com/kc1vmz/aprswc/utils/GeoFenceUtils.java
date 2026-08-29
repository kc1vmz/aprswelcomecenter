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

import com.kc1vmz.aprswc.enumeration.DistanceUnit;
import com.kc1vmz.aprswc.enumeration.RegionType;
import com.kc1vmz.aprswc.object.StationPosition;
import com.kc1vmz.aprswc.object.WelcomeRegion;
import org.springframework.stereotype.Component;

@Component
public class GeoFenceUtils {
    public boolean isInGeofenceCircle(
            String longitudeCenter,
            String latitudeCenter,
            int diameter,
            DistanceUnit unit,
            StationPosition stationPosition) {
        if ((longitudeCenter == null) || (latitudeCenter == null) || (stationPosition == null)) {
            return false;
        }

        boolean ret = false;
        try {
            double proximityInFeet = convertToFeet(diameter, unit);
            // get location of target
            // get location of checked
            // determine distance from each in radians
            long R = 6371000L;
            double meterToFeet = 3.28084;
            double lonTarget = ConvertLonLat.convertLongitude(longitudeCenter);
            double latTarget = ConvertLonLat.convertLatitude(latitudeCenter);
            double lonChecked = ConvertLonLat.convertLongitude(stationPosition.getLongitude());
            double latChecked = ConvertLonLat.convertLatitude(stationPosition.getLatitude());
            double phiTarget, phiChecked;
            double deltaLon, deltaLat;

            phiTarget = Math.toRadians(latTarget);
            phiChecked = Math.toRadians(latChecked);
            deltaLat = Math.toRadians(latTarget - latChecked);
            deltaLon = Math.toRadians(lonTarget - lonChecked);
            double a = Math.sin(Math.pow(deltaLat / 2, 2)
                    + Math.cos(phiTarget) * Math.cos(phiChecked) * Math.pow(Math.sin(deltaLon / 2), 2));
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            double feet = R * c * meterToFeet;

            if (feet <= proximityInFeet) {
                ret = true;
            }
        } catch (Exception e) {
        }

        return ret;
    }

    private double convertToFeet(int distance, DistanceUnit unit) {
        double ret = 0;
        switch (unit) {
            case FEET:
                ret = distance;
                break;
            case MILES:
                ret = distance * 5280;
                break;
            case METERS:
                ret = distance * 3.28084;
                break;
            case KILOMETERS:
                ret = distance * 3.28084 * 1000;
                break;
        }

        return ret;
    }

    public boolean isInGeoFenceRegion(WelcomeRegion region, StationPosition position) {
        if ((region == null) || (position == null)) {
            return false;
        }
        if (region.getType().equals(RegionType.CIRCLE)) {
            return isInGeofenceCircle(
                    region.getCenterLongitude(),
                    region.getCenterLatitude(),
                    region.getDiameter(),
                    region.getUnit(),
                    position);
        } else if (region.getType().equals(RegionType.RECTANGLE)) {
            return isInGeoFenceRectangle(
                    region.getTopLeftLongitude(),
                    region.getTopLeftLatitude(),
                    region.getBottomRightLongitude(),
                    region.getBottomRightLatitude(),
                    position);
        }

        return false;
    }

    public boolean isInGeoFenceRectangle(
            String longitudeTopLeft,
            String latitudeTopLeft,
            String longitudeBottomRight,
            String latitudeBottomRight,
            StationPosition stationPosition) {

        if ((longitudeTopLeft == null)
                || (latitudeTopLeft == null)
                || (longitudeBottomRight == null)
                || (latitudeBottomRight == null)
                || (stationPosition == null)) {
            return false;
        }

        // Initialize boundaries with the first corner's coordinates
        double dlongitudeTopLeft = ConvertLonLat.convertLongitude(longitudeTopLeft);
        double dlongitudeBottomRight = ConvertLonLat.convertLongitude(longitudeBottomRight);
        double dlatitudeTopLeft = ConvertLonLat.convertLatitude(latitudeTopLeft);
        double dlatitudeBottomRight = ConvertLonLat.convertLatitude(latitudeBottomRight);

        double xMin = dlongitudeTopLeft, xMax = dlongitudeBottomRight;
        double yMin = dlatitudeTopLeft, yMax = dlatitudeBottomRight;
        if (xMin > dlongitudeBottomRight) xMin = dlongitudeBottomRight;
        if (xMax < dlongitudeTopLeft) xMax = dlongitudeTopLeft;
        if (yMin > dlatitudeBottomRight) yMin = dlatitudeBottomRight;
        if (yMax < dlatitudeTopLeft) yMax = dlatitudeTopLeft;

        // Extract test point coordinates
        double x = ConvertLonLat.convertLongitude(stationPosition.getLongitude());
        double y = ConvertLonLat.convertLatitude(stationPosition.getLatitude());

        // Check if the point falls within the bounding box
        return x >= xMin && x <= xMax && y >= yMin && y <= yMax;
    }
}
