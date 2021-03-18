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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.callcontext.TenantContext;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDepositPaymentControlPluginApi extends TestBase {

    @Test(groups = "fast")
    public void testNoOp() throws Exception {
        final DepositPaymentControlPluginApi depositPaymentControlPluginApi = buildDepositPaymentControlPluginApi(ImmutableList.<String>of());

        final PaymentControlContext paymentControlContext = Mockito.mock(PaymentControlContext.class);
        final UUID tenantId = context.getTenantId();
        Mockito.when(paymentControlContext.getTenantId()).thenReturn(tenantId);

        Mockito.when(paymentControlContext.getAmount()).thenReturn(new BigDecimal("0.49"));
        Mockito.when(paymentControlContext.getCurrency()).thenReturn(Currency.USD);
        Assert.assertFalse(depositPaymentControlPluginApi.priorCall(paymentControlContext, ImmutableList.<PluginProperty>of()).isAborted());

        Mockito.when(paymentControlContext.getCurrency()).thenReturn(Currency.GBP);
        Assert.assertFalse(depositPaymentControlPluginApi.priorCall(paymentControlContext, ImmutableList.<PluginProperty>of()).isAborted());
    }

    @Test(groups = "fast")
    public void testAbort() throws Exception {
        final String rawConfig = "!!org.killbill.billing.plugin.deposit.DepositConfiguration\n" +
                                 "  minAmounts:\n" +
                                 "    USD: 0.5";
        final DepositPaymentControlPluginApi depositPaymentControlPluginApi = buildDepositPaymentControlPluginApi(ImmutableList.<String>of(rawConfig));

        final PaymentControlContext paymentControlContext = Mockito.mock(PaymentControlContext.class);
        final UUID tenantId = context.getTenantId();
        Mockito.when(paymentControlContext.getTenantId()).thenReturn(tenantId);

        Mockito.when(paymentControlContext.getAmount()).thenReturn(new BigDecimal("0.49"));
        Mockito.when(paymentControlContext.getCurrency()).thenReturn(Currency.USD);
        Assert.assertTrue(depositPaymentControlPluginApi.priorCall(paymentControlContext, ImmutableList.<PluginProperty>of()).isAborted());

        Mockito.when(paymentControlContext.getCurrency()).thenReturn(Currency.GBP);
        Assert.assertFalse(depositPaymentControlPluginApi.priorCall(paymentControlContext, ImmutableList.<PluginProperty>of()).isAborted());
    }

    private DepositPaymentControlPluginApi buildDepositPaymentControlPluginApi(final List<String> configs) throws TenantApiException {
        final TenantUserApi tenantUserApi = Mockito.mock(TenantUserApi.class);
        Mockito.when(tenantUserApi.getTenantValuesForKey(Mockito.eq("PLUGIN_CONFIG_" + DepositActivator.PLUGIN_NAME),
                                                         Mockito.any(TenantContext.class)))
               .thenReturn(configs);
        Mockito.when(killbillApi.getTenantUserApi()).thenReturn(tenantUserApi);

        final DepositConfigurationHandler configurationHandler = new DepositConfigurationHandler(null,
                                                                                                 DepositActivator.PLUGIN_NAME,
                                                                                                 killbillApi);
        configurationHandler.setDefaultConfigurable(new DepositConfiguration());

        return new DepositPaymentControlPluginApi(configurationHandler,
                                                  killbillApi,
                                                  configPropertiesService,
                                                  clock);
    }
}
