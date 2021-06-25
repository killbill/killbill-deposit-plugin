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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Named;
import javax.inject.Singleton;

import org.joda.time.DateTime;
import org.jooby.Result;
import org.jooby.Results;
import org.jooby.Status;
import org.jooby.mvc.Body;
import org.jooby.mvc.Header;
import org.jooby.mvc.Local;
import org.jooby.mvc.POST;
import org.jooby.mvc.Path;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillClock;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.plugin.api.core.PluginPaymentOptions;
import org.killbill.billing.plugin.api.payment.PluginPaymentMethodPlugin;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

@Singleton
@Path("/")
public class DepositServlet {

    private static final Logger logger = LoggerFactory.getLogger(DepositServlet.class);

    private final OSGIKillbillAPI killbillAPI;
    private final OSGIKillbillClock clock;

    @Inject
    public DepositServlet(final OSGIKillbillAPI killbillAPI, final OSGIKillbillClock clock) {
        this.killbillAPI = killbillAPI;
        this.clock = clock;
    }

    @POST
    @Path("/record")
    public Result recordPayments(@Body final DepositJson depositJson,
                                 @Header("X-Request-Id") final Optional<String> xRequestId,
                                 @Header("X-Killbill-Createdby") final Optional<String> createdBy,
                                 @Header("X-Killbill-Reason") final Optional<String> reason,
                                 @Header("X-Killbill-Comment") final Optional<String> comment,
                                 @Local @Named("killbill_tenant") final Tenant tenant) throws PaymentApiException {
        final DateTime utcNow = clock.getClock().getUTCNow();
        final CallContext callContext = new PluginCallContext(getOrCreateUserToken(xRequestId),
                                                              createdBy.orElse(DepositActivator.PLUGIN_NAME),
                                                              CallOrigin.EXTERNAL,
                                                              UserType.ADMIN,
                                                              reason.orElse(null),
                                                              comment.orElse(null),
                                                              utcNow,
                                                              utcNow,
                                                              depositJson.accountId,
                                                              tenant.getId());

        final Account account;
        try {
            account = killbillAPI.getAccountUserApi().getAccountById(depositJson.accountId, callContext);
        } catch (final AccountApiException e) {
            if (e.getCode() == ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID.getCode()) {
                logger.info("Account not found for accountId='{}'", depositJson.accountId);
                return Results.with(Status.NOT_FOUND);
            } else {
                logger.warn("Error retrieving accountId='{}'", depositJson.accountId, e);
                return Results.with(Status.SERVER_ERROR);
            }
        }

        final UUID depositPaymentMethodId = getOrCreateDepositPaymentMethod(callContext, account);

        if (depositJson.paymentReferenceNumber == null || depositJson.depositType == null || depositJson.effectiveDate == null) {
            return Results.with(Status.BAD_REQUEST);
        }

        final Iterable<PluginProperty> purchasePluginProperties = ImmutableList.<PluginProperty>of(
                new PluginProperty(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_PAYMENT_REFERENCE_NUMBER, depositJson.paymentReferenceNumber, false),
                new PluginProperty(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_TYPE, depositJson.depositType, false),
                new PluginProperty(DepositPaymentPluginApi.PLUGIN_PROPERTY_DEPOSIT_EFFECTIVE_DATE, depositJson.effectiveDate, false)
                                                                                                  );

        for (final InvoiceDepositJson invoiceDepositJson : depositJson.payments) {
            if (invoiceDepositJson.paymentAmount == null || invoiceDepositJson.paymentAmount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            final Invoice invoice;
            try {
                invoice = killbillAPI.getInvoiceUserApi().getInvoiceByNumber(invoiceDepositJson.invoiceNumber, callContext);
            } catch (final InvoiceApiException e) {
                if (e.getCode() == ErrorCode.INVOICE_NOT_FOUND.getCode()) {
                    logger.info("Invoice not found for invoiceNumber='{}'", invoiceDepositJson.invoiceNumber);
                    return Results.with(Status.NOT_FOUND);
                } else {
                    logger.warn("Error retrieving invoiceNumber='{}'", invoiceDepositJson.invoiceNumber, e);
                    return Results.with(Status.SERVER_ERROR);
                }
            }

            try {
                killbillAPI.getInvoicePaymentApi().createPurchaseForInvoicePayment(account,
                                                                                   invoice.getId(),
                                                                                   depositPaymentMethodId,
                                                                                   null,
                                                                                   invoiceDepositJson.paymentAmount,
                                                                                   invoice.getCurrency(),
                                                                                   depositJson.effectiveDate,
                                                                                   null,
                                                                                   null,
                                                                                   purchasePluginProperties,
                                                                                   new PluginPaymentOptions(),
                                                                                   callContext);
            } catch (final PaymentApiException e) {
                if (e.getCode() == ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode()) {
                    logger.info("Payment aborted for invoiceNumber='{}'", invoiceDepositJson.invoiceNumber);
                    return Results.with(Status.UNPROCESSABLE_ENTITY);
                } else {
                    logger.warn("Error paying invoiceNumber='{}'", invoiceDepositJson.invoiceNumber, e);
                    return Results.with(Status.SERVER_ERROR);
                }
            }
        }

        return Results.with(Status.CREATED);
    }

    private UUID getOrCreateDepositPaymentMethod(final CallContext callContext, final Account account) throws PaymentApiException {
        final List<PaymentMethod> accountPaymentMethods = killbillAPI.getPaymentApi().getAccountPaymentMethods(account.getId(),
                                                                                                               false,
                                                                                                               false,
                                                                                                               ImmutableList.<PluginProperty>of(),
                                                                                                               callContext);
        UUID depositPaymentMethodId = null;
        for (final PaymentMethod paymentMethod : accountPaymentMethods) {
            if (paymentMethod.getPluginName().equals(DepositActivator.PLUGIN_NAME)) {
                depositPaymentMethodId = paymentMethod.getId();
                break;
            }
        }

        if (depositPaymentMethodId == null) {
            depositPaymentMethodId = killbillAPI.getPaymentApi().addPaymentMethod(account,
                                                                                  null,
                                                                                  DepositActivator.PLUGIN_NAME,
                                                                                  false,
                                                                                  new PluginPaymentMethodPlugin(null, null, false, ImmutableList.<PluginProperty>of()),
                                                                                  ImmutableList.<PluginProperty>of(),
                                                                                  callContext);
        }
        return depositPaymentMethodId;
    }

    // Use X-Request-Id if this is provided and looks like a UUID, if not allocate a random one.
    public static UUID getOrCreateUserToken(final Optional<String> xRequestId) {
        UUID userToken;
        if (xRequestId.isPresent()) {
            try {
                userToken = UUID.fromString(xRequestId.get());
            } catch (final IllegalArgumentException ignored) {
                userToken = UUID.randomUUID();
            }
        } else {
            userToken = UUID.randomUUID();
        }
        return userToken;
    }

    private static final class DepositJson {

        public UUID accountId;
        public DateTime effectiveDate;
        public String paymentReferenceNumber;
        public String depositType;
        public Collection<InvoiceDepositJson> payments;

        @JsonCreator
        public DepositJson(@JsonProperty("accountId") final UUID accountId,
                           @JsonProperty("effectiveDate") final DateTime effectiveDate,
                           @JsonProperty("paymentReferenceNumber") final String paymentReferenceNumber,
                           @JsonProperty("depositType") final String depositType,
                           @JsonProperty("payments") final Collection<InvoiceDepositJson> payments) {
            this.accountId = accountId;
            this.effectiveDate = effectiveDate;
            this.paymentReferenceNumber = paymentReferenceNumber;
            this.depositType = depositType;
            this.payments = payments;
        }

        @Override
        public String toString() {
            return "DepositJson{" +
                   "accountId=" + accountId +
                   ", effectiveDate=" + effectiveDate +
                   ", paymentReferenceNumber='" + paymentReferenceNumber + '\'' +
                   ", depositType='" + depositType + '\'' +
                   ", payments=" + payments +
                   '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final DepositJson that = (DepositJson) o;

            if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
                return false;
            }
            if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
                return false;
            }
            if (paymentReferenceNumber != null ? !paymentReferenceNumber.equals(that.paymentReferenceNumber) : that.paymentReferenceNumber != null) {
                return false;
            }
            if (depositType != null ? !depositType.equals(that.depositType) : that.depositType != null) {
                return false;
            }
            return payments != null ? payments.equals(that.payments) : that.payments == null;
        }

        @Override
        public int hashCode() {
            int result = accountId != null ? accountId.hashCode() : 0;
            result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
            result = 31 * result + (paymentReferenceNumber != null ? paymentReferenceNumber.hashCode() : 0);
            result = 31 * result + (depositType != null ? depositType.hashCode() : 0);
            result = 31 * result + (payments != null ? payments.hashCode() : 0);
            return result;
        }
    }

    private static final class InvoiceDepositJson {

        public Integer invoiceNumber;
        public BigDecimal paymentAmount;

        @JsonCreator
        public InvoiceDepositJson(@JsonProperty("invoiceNumber") final Integer invoiceNumber,
                                  @JsonProperty("paymentAmount") final BigDecimal paymentAmount) {
            this.invoiceNumber = invoiceNumber;
            this.paymentAmount = paymentAmount;
        }

        @Override
        public String toString() {
            return "InvoiceDepositJson{" +
                   "invoiceNumber=" + invoiceNumber +
                   ", paymentAmount=" + paymentAmount +
                   '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final InvoiceDepositJson that = (InvoiceDepositJson) o;

            if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
                return false;
            }
            return paymentAmount != null ? paymentAmount.equals(that.paymentAmount) : that.paymentAmount == null;
        }

        @Override
        public int hashCode() {
            int result = invoiceNumber != null ? invoiceNumber.hashCode() : 0;
            result = 31 * result + (paymentAmount != null ? paymentAmount.hashCode() : 0);
            return result;
        }
    }
}
