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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.kc1vmz.aprswc.object.WelcomeCenterWeatherReport;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherSummary;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class WelcomeCenterWeatherReportAccessorTest {
    @Test
    void findSummaryAveragesEachAvailableMeasurement() {
        UUID welcomeCenterId = UUID.randomUUID();
        LocalDateTime olderTime = LocalDateTime.of(2026, 8, 22, 10, 0);
        LocalDateTime newerTime = olderTime.plusMinutes(10);
        WelcomeCenterWeatherReport first =
                new WelcomeCenterWeatherReport(null, "CHANGEME", null, null, 60.0f, 40.0f, null, 800.0f, olderTime);
        WelcomeCenterWeatherReport second =
                new WelcomeCenterWeatherReport(null, "CHANGEME", null, null, 80.0f, 60.0f, 30.0f, 1000.0f, newerTime);
        WelcomeCenterWeatherReportAccessor accessor = spy(new WelcomeCenterWeatherReportAccessor());
        doReturn(Flux.just(first, second)).when(accessor).findByWelcomeCenterId(welcomeCenterId);

        LocalDateTime beforeSummary = LocalDateTime.now();
        WelcomeCenterWeatherSummary summary =
                accessor.findSummary(welcomeCenterId).block();
        LocalDateTime afterSummary = LocalDateTime.now();

        assertAll(
                () -> assertEquals(welcomeCenterId, summary.getWelcomeCenterId()),
                () -> assertEquals(70.0f, summary.getTemperature()),
                () -> assertEquals(50.0f, summary.getHumidity()),
                () -> assertEquals(30.0f, summary.getBarometricPressure()),
                () -> assertEquals(900.0f, summary.getLuminosity()),
                () -> assertFalse(summary.getReportTime().isBefore(beforeSummary)),
                () -> assertFalse(summary.getReportTime().isAfter(afterSummary)));
        verify(accessor).findByWelcomeCenterId(welcomeCenterId);
    }
}
