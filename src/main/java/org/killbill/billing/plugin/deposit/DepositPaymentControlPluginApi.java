/*
 * Copyright 2020-2021 Equinix, Inc
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

import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.control.PluginPaymentControlPluginApi;
import org.killbill.billing.plugin.api.control.PluginPriorPaymentControlResult;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DepositPaymentControlPluginApi extends PluginPaymentControlPluginApi {

    private static final Logger logger = LoggerFactory.getLogger(DepositPaymentControlPluginApi.class);

    private final DepositConfigurationHandler depositConfigurationHandler;

    public DepositPaymentControlPluginApi(final DepositConfigurationHandler depositConfigurationHandler,
                                          final OSGIKillbillAPI killbillAPI,
                                          final OSGIConfigPropertiesService configProperties,
                                          final Clock clock) {
        super(killbillAPI, configProperties, clock);
        this.depositConfigurationHandler = depositConfigurationHandler;
    }

    @Override
    public PriorPaymentControlResult priorCall(final PaymentControlContext context,
                                               final Iterable<PluginProperty> properties) throws PaymentControlApiException {
        final DepositConfiguration depositConfiguration = depositConfigurationHandler.getConfigurable(context.getTenantId());
        if (context.getAmount() == null ||
            depositConfiguration == null ||
            depositConfiguration.minAmounts == null ||
            depositConfiguration.minAmounts.get(context.getCurrency()) == null ||
            depositConfiguration.minAmounts.get(context.getCurrency()).compareTo(context.getAmount()) <= 0) {
            return new PluginPriorPaymentControlResult(false);
        }

        logger.info("Aborting payment: amount='{}', minAmount='{}'", context.getAmount(), depositConfiguration.minAmounts.get(context.getCurrency()));
        return new PluginPriorPaymentControlResult(true);
    }
}
