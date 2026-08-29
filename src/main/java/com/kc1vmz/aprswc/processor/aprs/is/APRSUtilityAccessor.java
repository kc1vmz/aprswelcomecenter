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
package com.kc1vmz.aprswc.processor.aprs.is;

import com.kc1vmz.aprswc.accessor.ApplicationVersionAccessor;
import com.kc1vmz.aprswc.object.ApplicationVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class APRSUtilityAccessor {
    private static final Logger logger = LogManager.getLogger(APRSUtilityAccessor.class);

    @Autowired
    private ApplicationVersionAccessor applicationVersionAccessor;

    public String generateGroupQuery(String group) {
        return String.format("g/%s", group);
    }

    /**
     * generate authentication string for APRS-IS server
     *
     * @param auth authentication information
     * @param filter filter to use on connect to receive packets
     * @return  authentication string
     */
    public String generateAuthStr(APRSAuthenticationInfo auth, String filter) {
        // user mycall[-ss] pass passcode[ vers softwarename softwarevers[ UDP udpport][ servercommand]]
        String ret;
        ApplicationVersion applicationVersion =
                applicationVersionAccessor.find().block();
        if ((filter != null) && (!filter.isEmpty())) {
            ret = String.format(
                    "user %s pass %s vers %s %s filter %s",
                    auth.getCallsign(),
                    auth.getPassword(),
                    applicationVersion.getApplicationName(),
                    applicationVersion.getVersion(),
                    filter);
        } else {
            ret = String.format(
                    "user %s pass %s vers %s %s",
                    auth.getCallsign(),
                    auth.getPassword(),
                    applicationVersion.getApplicationName(),
                    applicationVersion.getVersion());
        }
        logger.debug(String.format("auth string generated: %s", ret));
        return ret;
    }
}
