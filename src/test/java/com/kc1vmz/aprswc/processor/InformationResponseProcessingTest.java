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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.kc1vmz.aprswc.accessor.WelcomeCenterAccessor;
import com.kc1vmz.aprswc.accessor.WelcomeCenterLifecycle;
import com.kc1vmz.aprswc.accessor.WelcomeCenterWeatherReportAccessor;
import com.kc1vmz.aprswc.content.InformationResponseText;
import com.kc1vmz.aprswc.database.ApplicationSettingsRepository;
import com.kc1vmz.aprswc.enumeration.StationCommandType;
import com.kc1vmz.aprswc.enumeration.WelcomeCenterStatus;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import com.kc1vmz.aprswc.object.StationCommand;
import com.kc1vmz.aprswc.object.StationMessage;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import com.kc1vmz.aprswc.object.WelcomeCenterSnapshot;
import com.kc1vmz.aprswc.object.WelcomeCenterWeatherSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class InformationResponseProcessingTest {
    @Test
    void lifecycleDailyAndWeatherUseServerLanguageWhileCommandsRemainEnglish() {
        var repository = mock(ApplicationSettingsRepository.class);
        var settings = new ApplicationSettings(null, null, ApplicationSettings.DEFAULT_TINY_TOPICS_SERVER_URL);
        settings.setInformationResponseLanguage("xx");
        when(repository.findAll()).thenReturn(List.of(settings));
        var text = new InformationResponseText(repository);
        var queue = mock(StationMessageQueue.class);
        var center = mock(WelcomeCenter.class);
        UUID id = UUID.randomUUID();
        when(center.getId()).thenReturn(id);
        when(center.getCallsign()).thenReturn("K4BO");
        when(center.isOpen()).thenReturn(true);
        when(center.getCommunicationMode()).thenReturn("ALL");
        var snapshot = new WelcomeCenterSnapshot(
                id, "Name", "K4BO", "OWNER", null, null, "c", "/", WelcomeCenterStatus.OPEN, null);
        var lifecycle = new WelcomeCenterLifecycle(mock(ObjectBeaconQueue.class), queue, text);
        lifecycle.onWelcomeCenterOpened(snapshot, snapshot);
        lifecycle.onWelcomeCenterClosed(snapshot, snapshot);
        var daily = new WelcomeCenterDailyProcessor(null, queue, text);
        daily.processWelcomeCenter(center);
        daily.stop();
        var centers = mock(WelcomeCenterAccessor.class);
        when(centers.findOpenById(id)).thenReturn(Mono.just(center));
        var weather = mock(WelcomeCenterWeatherReportAccessor.class);
        when(weather.findSummary(id))
                .thenReturn(Mono.just(new WelcomeCenterWeatherSummary(id, 70f, 50f, 1010f, 10f, null)));
        var commands = new StationCommandProcessor(null, queue, weather, centers, null, null, null, null, text);
        commands.processCommand(new StationCommand(null, "N2EDX-7", center, null, "WX", StationCommandType.UNKNOWN));
        when(weather.findSummary(id)).thenReturn(Mono.empty());
        commands.processCommand(
                new StationCommand(null, "N2EDX-7", center, null, "WEATHER", StationCommandType.UNKNOWN));
        commands.stop();
        var messages = ArgumentCaptor.forClass(StationMessage.class);
        verify(queue, times(5)).offer(messages.capture());
        assertThat(messages.getAllValues())
                .allSatisfy(message ->
                        assertThat(message.getContent()).startsWith("xx").endsWith("xx"));
        assertThat(messages.getAllValues().get(0).getContent()).contains("send HELP");
        assertThat(messages.getAllValues().get(3).getContent()).contains("Temp: 70F");
        assertThat(messages.getAllValues().get(4).getContent()).contains("No current weather summary available");
    }
}
