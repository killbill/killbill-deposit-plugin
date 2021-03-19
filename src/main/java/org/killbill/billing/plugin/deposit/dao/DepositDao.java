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

package org.killbill.billing.plugin.deposit.dao;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.sql.DataSource;

import org.joda.time.DateTime;
import org.jooq.impl.DSL;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.dao.payment.PluginPaymentDao;
import org.killbill.billing.plugin.deposit.DepositPaymentPluginApi;
import org.killbill.billing.plugin.deposit.dao.gen.tables.DepositPaymentMethods;
import org.killbill.billing.plugin.deposit.dao.gen.tables.DepositResponses;
import org.killbill.billing.plugin.deposit.dao.gen.tables.records.DepositPaymentMethodsRecord;
import org.killbill.billing.plugin.deposit.dao.gen.tables.records.DepositResponsesRecord;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.collect.ImmutableMap;

import static org.killbill.billing.plugin.deposit.dao.gen.tables.DepositPaymentMethods.DEPOSIT_PAYMENT_METHODS;
import static org.killbill.billing.plugin.deposit.dao.gen.tables.DepositResponses.DEPOSIT_RESPONSES;

public class DepositDao extends PluginPaymentDao<DepositResponsesRecord, DepositResponses, DepositPaymentMethodsRecord, DepositPaymentMethods> {

    public DepositDao(final DataSource dataSource) throws SQLException {
        super(DEPOSIT_RESPONSES, DEPOSIT_PAYMENT_METHODS, dataSource);
        // Save space in the database
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
    }

    public void addPaymentMethod(final UUID kbAccountId,
                                 final UUID kbPaymentMethodId,
                                 final Map<String, Object> additionalDataMap,
                                 final DateTime utcNow,
                                 final UUID kbTenantId) throws SQLException {
        execute(dataSource.getConnection(),
                new WithConnectionCallback<DepositResponsesRecord>() {
                    @Override
                    public DepositResponsesRecord withConnection(final Connection conn) throws SQLException {
                        DSL.using(conn, dialect, settings)
                           .insertInto(DEPOSIT_PAYMENT_METHODS,
                                       DEPOSIT_PAYMENT_METHODS.KB_ACCOUNT_ID,
                                       DEPOSIT_PAYMENT_METHODS.KB_PAYMENT_METHOD_ID,
                                       DEPOSIT_PAYMENT_METHODS.IS_DELETED,
                                       DEPOSIT_PAYMENT_METHODS.ADDITIONAL_DATA,
                                       DEPOSIT_PAYMENT_METHODS.CREATED_DATE,
                                       DEPOSIT_PAYMENT_METHODS.UPDATED_DATE,
                                       DEPOSIT_PAYMENT_METHODS.KB_TENANT_ID)
                           .values(kbAccountId.toString(),
                                   kbPaymentMethodId.toString(),
                                   (short) FALSE,
                                   asString(additionalDataMap),
                                   toLocalDateTime(utcNow),
                                   toLocalDateTime(utcNow),
                                   kbTenantId.toString()
                                  )
                           .execute();

                        return null;
                    }
                });
    }

    public void addResponse(final UUID kbAccountId,
                            final UUID kbPaymentId,
                            final UUID kbPaymentTransactionId,
                            final TransactionType transactionType,
                            final BigDecimal amount,
                            final Currency currency,
                            final Iterable<PluginProperty> properties,
                            final DateTime utcNow,
                            final UUID kbTenantId) throws SQLException {
        final Map additionalDataMap = PluginProperties.toStringMap(properties);

        execute(dataSource.getConnection(),
                new WithConnectionCallback<DepositResponsesRecord>() {
                    @Override
                    public DepositResponsesRecord withConnection(final Connection conn) throws SQLException {
                        final String depositEffectiveDateOrNull = PluginProperties.findPluginPropertyValue(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_EFFECTIVE_DATE, properties);
                        final LocalDateTime localDepositEffectiveDate = depositEffectiveDateOrNull == null ? null : toLocalDateTime(new DateTime(depositEffectiveDateOrNull));
                        DSL.using(conn, dialect, settings)
                           .insertInto(DEPOSIT_RESPONSES,
                                       DEPOSIT_RESPONSES.KB_ACCOUNT_ID,
                                       DEPOSIT_RESPONSES.KB_PAYMENT_ID,
                                       DEPOSIT_RESPONSES.KB_PAYMENT_TRANSACTION_ID,
                                       DEPOSIT_RESPONSES.TRANSACTION_TYPE,
                                       DEPOSIT_RESPONSES.AMOUNT,
                                       DEPOSIT_RESPONSES.CURRENCY,
                                       DEPOSIT_RESPONSES.DEPOSIT_TYPE,
                                       DEPOSIT_RESPONSES.DEPOSIT_REFERENCE_NUMBER,
                                       DEPOSIT_RESPONSES.DEPOSIT_EFFECTIVE_DATE,
                                       DEPOSIT_RESPONSES.ADDITIONAL_DATA,
                                       DEPOSIT_RESPONSES.CREATED_DATE,
                                       DEPOSIT_RESPONSES.KB_TENANT_ID)
                           .values(kbAccountId.toString(),
                                   kbPaymentId.toString(),
                                   kbPaymentTransactionId.toString(),
                                   transactionType.toString(),
                                   amount,
                                   currency == null ? null : currency.name(),
                                   PluginProperties.findPluginPropertyValue(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_TYPE, properties),
                                   PluginProperties.findPluginPropertyValue(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_PAYMENT_REFERENCE_NUMBER, properties),
                                   localDepositEffectiveDate,
                                   asString(additionalDataMap),
                                   toLocalDateTime(utcNow),
                                   kbTenantId.toString())
                           .execute();

                        return null;
                    }
                });
    }

    public static Map fromAdditionalData(@Nullable final String additionalData) {
        if (additionalData == null) {
            return ImmutableMap.of();
        }

        try {
            return objectMapper.readValue(additionalData, Map.class);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
