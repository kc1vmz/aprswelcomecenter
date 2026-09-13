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

import com.kc1vmz.aprswc.database.*;
import com.kc1vmz.aprswc.enumeration.CommunicationEventType;
import com.kc1vmz.aprswc.object.CommunicationPolicy;
import com.kc1vmz.aprswc.object.WelcomeCenter;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Synchronous JPA transactions, invoked inside the accessors' bounded-elastic work. */
@Service
@Transactional
public class ContainmentDeletionService {
    @Autowired
    private WelcomeCenterRepository centers;

    @Autowired
    private CommunicationCategoryRepository categories;

    @Autowired
    private CommunicationPolicyRepository policies;

    @Autowired
    private AnnouncementRepository announcements;

    @Autowired
    private CommunicationEventRepository events;

    @Autowired
    private StationCommandRepository commands;

    @Autowired
    private StationMessageRepository messages;

    public WelcomeCenter deleteWelcomeCenter(UUID id) {
        WelcomeCenter center =
                centers.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        announcements.deleteAllByWelcomeCenterId(id);
        policies.findByWelcomeCenterId(id).forEach(this::deletePolicyChildren);
        events.deleteAllByWelcomeCenterId(id);
        commands.deleteAllByWelcomeCenterId(id);
        messages.deleteAllByWelcomeCenterId(id);
        centers.delete(center); // Regions are owned by the center and cascade on removal.
        return center;
    }

    public void deletePolicy(UUID id) {
        deletePolicyChildren(
                policies.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public void deleteCategory(UUID id) {
        var category = categories.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        policies.findByCategoryId(id).forEach(this::deletePolicyChildren);
        categories.delete(category);
    }

    private void deletePolicyChildren(CommunicationPolicy policy) {
        announcements.deleteAllByCommunicationPolicyId(policy.getId());
        // ENTER/EXIT events use the policy UUID as context, without a database foreign key.
        events.deleteAllByContextAndTypeIn(
                policy.getId(), List.of(CommunicationEventType.ENTER_REGION, CommunicationEventType.EXIT_REGION));
        policies.delete(policy);
        policies.flush();
    }
}
