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

import org.joda.time.DateTime;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.plugin.TestUtils;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.payment.PluginPaymentMethodPlugin;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDepositPaymentPluginApi extends TestBase {

    @Test(groups = "slow")
    public void testPurchased() throws PaymentPluginApiException, PaymentApiException {
        final UUID kbAccountId = account.getId();
        final UUID kbPaymentMethodId = UUID.randomUUID();
        depositPaymentPluginApi.addPaymentMethod(kbAccountId,
                                                 kbPaymentMethodId,
                                                 new PluginPaymentMethodPlugin(kbPaymentMethodId, null, false, ImmutableList.of()),
                                                 false,
                                                 ImmutableList.<PluginProperty>of(),
                                                 context);

        final Payment payment = TestUtils.buildPayment(account.getId(), kbPaymentMethodId, account.getCurrency(), killbillApi);
        final PaymentTransaction purchaseTransaction = TestUtils.buildPaymentTransaction(payment, TransactionType.PURCHASE, BigDecimal.TEN, payment.getCurrency());

        final String refNumber = "WIRE-12345";
        final String depositType = "wire";
        final DateTime depositEffectiveDate = new DateTime("2012-02-01");

        // See DepositServlet
        final Iterable<PluginProperty> purchasePluginProperties = ImmutableList.<PluginProperty>of(
                new PluginProperty(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_PAYMENT_REFERENCE_NUMBER, refNumber, false),
                new PluginProperty(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_TYPE, depositType, false),
                new PluginProperty(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_EFFECTIVE_DATE, depositEffectiveDate, false)
                                                                                                  );

        final PaymentTransactionInfoPlugin purchaseInfoPlugin = depositPaymentPluginApi.purchasePayment(account.getId(),
                                                                                                        payment.getId(),
                                                                                                        purchaseTransaction.getId(),
                                                                                                        kbPaymentMethodId,
                                                                                                        purchaseTransaction.getAmount(),
                                                                                                        purchaseTransaction.getCurrency(),
                                                                                                        purchasePluginProperties,
                                                                                                        context);
        Assert.assertEquals(purchaseInfoPlugin.getAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(purchaseInfoPlugin.getTransactionType(), TransactionType.PURCHASE);
        Assert.assertEquals(purchaseInfoPlugin.getFirstPaymentReferenceId(), refNumber);
        Assert.assertEquals(PluginProperties.findPluginPropertyValue(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_TYPE, purchaseInfoPlugin.getProperties()), depositType);
        Assert.assertEquals(PluginProperties.findPluginPropertyValue(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_EFFECTIVE_DATE, purchaseInfoPlugin.getProperties()), depositEffectiveDate.toString());

        final List<PaymentTransactionInfoPlugin> paymentInfo = depositPaymentPluginApi.getPaymentInfo(kbAccountId,
                                                                                                      payment.getId(),
                                                                                                      ImmutableList.<PluginProperty>of(),
                                                                                                      context);
        Assert.assertEquals(paymentInfo.size(), 1);
        Assert.assertEquals(paymentInfo.get(0), purchaseInfoPlugin);
    }

    @Test(groups = "slow")
    public void testAddPaymentMethod() throws PaymentPluginApiException {
        final UUID kbAccountId = account.getId();

        final List<PaymentMethodInfoPlugin> noPms = depositPaymentPluginApi.getPaymentMethods(kbAccountId,
                                                                                              false,
                                                                                              ImmutableList.<PluginProperty>of(),
                                                                                              context);
        Assert.assertEquals(noPms.size(), 0);

        final UUID kbPaymentMethodId = UUID.randomUUID();
        depositPaymentPluginApi.addPaymentMethod(kbAccountId,
                                                 kbPaymentMethodId,
                                                 new PluginPaymentMethodPlugin(kbPaymentMethodId, null, false, ImmutableList.of()),
                                                 false,
                                                 ImmutableList.<PluginProperty>of(),
                                                 context);

        final List<PaymentMethodInfoPlugin> pms = depositPaymentPluginApi.getPaymentMethods(kbAccountId,
                                                                                            false,
                                                                                            ImmutableList.<PluginProperty>of(),
                                                                                            context);
        Assert.assertEquals(pms.size(), 1);
        Assert.assertEquals(pms.get(0).getPaymentMethodId(), kbPaymentMethodId);
    }
}
