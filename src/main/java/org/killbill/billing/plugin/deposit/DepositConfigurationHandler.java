/*
 * Copyright 2021-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.plugin.deposit;

import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.plugin.core.config.YAMLPluginTenantConfigurationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DepositConfigurationHandler extends YAMLPluginTenantConfigurationHandler<DepositConfiguration, DepositConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger(DepositConfigurationHandler.class);

    private final String region;

    public DepositConfigurationHandler(final String region,
                                       final String pluginName,
                                       final OSGIKillbillAPI osgiKillbillAPI) {
        super(pluginName, osgiKillbillAPI);
        this.region = region;
    }
}
