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

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.payment.PluginPaymentMethodInfoPlugin;
import org.killbill.billing.plugin.api.payment.PluginPaymentMethodPlugin;
import org.killbill.billing.plugin.api.payment.PluginPaymentPluginApi;
import org.killbill.billing.plugin.api.payment.PluginPaymentTransactionInfoPlugin;
import org.killbill.billing.plugin.deposit.dao.DepositDao;
import org.killbill.billing.plugin.deposit.dao.gen.tables.DepositPaymentMethods;
import org.killbill.billing.plugin.deposit.dao.gen.tables.DepositResponses;
import org.killbill.billing.plugin.deposit.dao.gen.tables.records.DepositPaymentMethodsRecord;
import org.killbill.billing.plugin.deposit.dao.gen.tables.records.DepositResponsesRecord;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DepositPaymentPluginApi extends PluginPaymentPluginApi<DepositResponsesRecord, DepositResponses, DepositPaymentMethodsRecord, DepositPaymentMethods> {

    public static final String PLUGIN_PROPERTY_DEPOSIT_PAYMENT_REFERENCE_NUMBER = "depositPaymentReferenceNumber";
    public static final String PLUGIN_PROPERTY_DEPOSIT_TYPE = "depositType";
    public static final String PLUGIN_PROPERTY_DEPOSIT_EFFECTIVE_DATE = "depositEffectiveDate";

    private final DepositDao dao;

    public DepositPaymentPluginApi(final OSGIKillbillAPI killbillAPI,
                                   final OSGIConfigPropertiesService configProperties,
                                   final Clock clock,
                                   final DepositDao dao) {
        super(killbillAPI, configProperties, clock, dao);
        this.dao = dao;
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId,
                                 final UUID kbPaymentMethodId,
                                 final PaymentMethodPlugin paymentMethodProps,
                                 final boolean setDefault,
                                 final Iterable<PluginProperty> properties,
                                 final CallContext context) throws PaymentPluginApiException {
        final Map<String, Object> mergedProperties = PluginProperties.toStringMap(new Iterable[]{paymentMethodProps.getProperties(), properties});
        final DateTime utcNow = this.clock.getUTCNow();

        try {
            this.dao.addPaymentMethod(kbAccountId, kbPaymentMethodId, mergedProperties, utcNow, context.getTenantId());
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("Unable to add payment method for kbPaymentMethodId " + kbPaymentMethodId, e);
        }
    }

    @Override
    public PaymentTransactionInfoPlugin purchasePayment(final UUID kbAccountId,
                                                        final UUID kbPaymentId,
                                                        final UUID kbTransactionId,
                                                        final UUID kbPaymentMethodId,
                                                        final BigDecimal amount,
                                                        final Currency currency,
                                                        final Iterable<PluginProperty> properties,
                                                        final CallContext context) throws PaymentPluginApiException {
        try {
            dao.addResponse(kbAccountId,
                            kbPaymentId,
                            kbTransactionId,
                            TransactionType.PURCHASE,
                            amount,
                            currency,
                            properties,
                            clock.getUTCNow(),
                            context.getTenantId());
            return Iterables.<PaymentTransactionInfoPlugin>getLast(getPaymentInfo(kbAccountId, kbPaymentId, properties, context));
        } catch (final SQLException e) {
            throw new PaymentPluginApiException("We encountered a database error", e);
        }
    }

    @Override
    protected PaymentTransactionInfoPlugin buildPaymentTransactionInfoPlugin(final DepositResponsesRecord record) {
        final Map additionalData = DepositDao.fromAdditionalData(record.getAdditionalData());
        final String firstPaymentReferenceId = (String) additionalData.get(PLUGIN_PROPERTY_DEPOSIT_PAYMENT_REFERENCE_NUMBER);

        final DateTime responseDate = new DateTime(record.getCreatedDate()
                                                         .atZone(ZoneOffset.UTC)
                                                         .toInstant()
                                                         .toEpochMilli(), DateTimeZone.UTC);

        return new PluginPaymentTransactionInfoPlugin(UUID.fromString(record.getKbPaymentId()),
                                                      UUID.fromString(record.getKbPaymentTransactionId()),
                                                      TransactionType.valueOf(record.getTransactionType()),
                                                      record.getAmount(),
                                                      Strings.isNullOrEmpty(record.getCurrency()) ? null : Currency.valueOf(record.getCurrency()),
                                                      PaymentPluginStatus.PROCESSED,
                                                      null,
                                                      null,
                                                      firstPaymentReferenceId,
                                                      null,
                                                      responseDate,
                                                      responseDate,
                                                      PluginProperties.buildPluginProperties(additionalData));
    }

    @Override
    protected PaymentMethodPlugin buildPaymentMethodPlugin(final DepositPaymentMethodsRecord record) {
        if (record == null) {
            return null;
        }
        return new PluginPaymentMethodPlugin(UUID.fromString(record.getKbPaymentMethodId()),
                                             String.valueOf(record.getRecordId()),
                                             record.getIsDefault() == DepositDao.TRUE,
                                             PluginProperties.buildPluginProperties(DepositDao.fromAdditionalData(record.getAdditionalData())));
    }

    @Override
    protected PaymentMethodInfoPlugin buildPaymentMethodInfoPlugin(final DepositPaymentMethodsRecord record) {
        if (record == null) {
            return null;
        }
        return new PluginPaymentMethodInfoPlugin(UUID.fromString(record.getKbAccountId()),
                                                 UUID.fromString(record.getKbPaymentMethodId()),
                                                 record.getIsDefault() == DepositDao.TRUE,
                                                 String.valueOf(record.getRecordId()));
    }

    @Override
    protected String getPaymentMethodId(final DepositPaymentMethodsRecord record) {
        if (record == null) {
            return null;
        }
        return record.getKbPaymentMethodId();
    }

    @Override
    public PaymentTransactionInfoPlugin authorizePayment(final UUID kbAccountId,
                                                         final UUID kbPaymentId,
                                                         final UUID kbTransactionId,
                                                         final UUID kbPaymentMethodId,
                                                         final BigDecimal amount,
                                                         final Currency currency,
                                                         final Iterable<PluginProperty> properties,
                                                         final CallContext context) throws PaymentPluginApiException {
        return new PluginPaymentTransactionInfoPlugin(kbPaymentId,
                                                      kbTransactionId,
                                                      TransactionType.AUTHORIZE,
                                                      amount,
                                                      currency,
                                                      PaymentPluginStatus.CANCELED,
                                                      "Unsupported operation",
                                                      null,
                                                      null,
                                                      null,
                                                      clock.getUTCNow(),
                                                      clock.getUTCNow(),
                                                      ImmutableList.<PluginProperty>of());
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId,
                                                       final UUID kbPaymentId,
                                                       final UUID kbTransactionId,
                                                       final UUID kbPaymentMethodId,
                                                       final BigDecimal amount,
                                                       final Currency currency,
                                                       final Iterable<PluginProperty> properties,
                                                       final CallContext context) throws PaymentPluginApiException {
        return new PluginPaymentTransactionInfoPlugin(kbPaymentId,
                                                      kbTransactionId,
                                                      TransactionType.CAPTURE,
                                                      amount,
                                                      currency,
                                                      PaymentPluginStatus.CANCELED,
                                                      "Unsupported operation",
                                                      null,
                                                      null,
                                                      null,
                                                      clock.getUTCNow(),
                                                      clock.getUTCNow(),
                                                      ImmutableList.<PluginProperty>of());
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId,
                                                    final UUID kbPaymentId,
                                                    final UUID kbTransactionId,
                                                    final UUID kbPaymentMethodId,
                                                    final Iterable<PluginProperty> properties,
                                                    final CallContext context) throws PaymentPluginApiException {
        return new PluginPaymentTransactionInfoPlugin(kbPaymentId,
                                                      kbTransactionId,
                                                      TransactionType.VOID,
                                                      null,
                                                      null,
                                                      PaymentPluginStatus.CANCELED,
                                                      "Unsupported operation",
                                                      null,
                                                      null,
                                                      null,
                                                      clock.getUTCNow(),
                                                      clock.getUTCNow(),
                                                      ImmutableList.<PluginProperty>of());
    }

    @Override
    public PaymentTransactionInfoPlugin creditPayment(final UUID kbAccountId,
                                                      final UUID kbPaymentId,
                                                      final UUID kbTransactionId,
                                                      final UUID kbPaymentMethodId,
                                                      final BigDecimal amount,
                                                      final Currency currency,
                                                      final Iterable<PluginProperty> properties,
                                                      final CallContext context) throws PaymentPluginApiException {
        return new PluginPaymentTransactionInfoPlugin(kbPaymentId,
                                                      kbTransactionId,
                                                      TransactionType.CREDIT,
                                                      amount,
                                                      currency,
                                                      PaymentPluginStatus.CANCELED,
                                                      "Unsupported operation",
                                                      null,
                                                      null,
                                                      null,
                                                      clock.getUTCNow(),
                                                      clock.getUTCNow(),
                                                      ImmutableList.<PluginProperty>of());
    }

    @Override
    public PaymentTransactionInfoPlugin refundPayment(final UUID kbAccountId,
                                                      final UUID kbPaymentId,
                                                      final UUID kbTransactionId,
                                                      final UUID kbPaymentMethodId,
                                                      final BigDecimal amount,
                                                      final Currency currency,
                                                      final Iterable<PluginProperty> properties,
                                                      final CallContext context) throws PaymentPluginApiException {
        return new PluginPaymentTransactionInfoPlugin(kbPaymentId,
                                                      kbTransactionId,
                                                      TransactionType.REFUND,
                                                      amount,
                                                      currency,
                                                      PaymentPluginStatus.CANCELED,
                                                      "Unsupported operation",
                                                      null,
                                                      null,
                                                      null,
                                                      clock.getUTCNow(),
                                                      clock.getUTCNow(),
                                                      ImmutableList.<PluginProperty>of());
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID kbAccountId,
                                                               final Iterable<PluginProperty> customFields,
                                                               final Iterable<PluginProperty> properties,
                                                               final CallContext context) throws PaymentPluginApiException {
        throw new PaymentPluginApiException("Unsupported operation", new UnsupportedOperationException("buildFormDescriptor"));
    }

    @Override
    public GatewayNotification processNotification(final String notification,
                                                   final Iterable<PluginProperty> properties,
                                                   final CallContext context) throws PaymentPluginApiException {
        throw new PaymentPluginApiException("Unsupported operation", new UnsupportedOperationException("processNotification"));
    }
}
