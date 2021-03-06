/*
 * This file is generated by jOOQ.
 */
package org.killbill.billing.plugin.deposit.dao.gen;


import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.Internal;
import org.killbill.billing.plugin.deposit.dao.gen.tables.DepositPaymentMethods;
import org.killbill.billing.plugin.deposit.dao.gen.tables.DepositResponses;


/**
 * A class modelling indexes of tables of the <code>killbill</code> schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index DEPOSIT_PAYMENT_METHODS_DEPOSIT_PAYMENT_METHODS_KB_PAYMENT_ID = Indexes0.DEPOSIT_PAYMENT_METHODS_DEPOSIT_PAYMENT_METHODS_KB_PAYMENT_ID;
    public static final Index DEPOSIT_RESPONSES_DEPOSIT_RESPONSES_DEPOSIT_REFERENCE_NUMBER = Indexes0.DEPOSIT_RESPONSES_DEPOSIT_RESPONSES_DEPOSIT_REFERENCE_NUMBER;
    public static final Index DEPOSIT_RESPONSES_DEPOSIT_RESPONSES_KB_PAYMENT_ID = Indexes0.DEPOSIT_RESPONSES_DEPOSIT_RESPONSES_KB_PAYMENT_ID;
    public static final Index DEPOSIT_RESPONSES_DEPOSIT_RESPONSES_KB_PAYMENT_TRANSACTION_ID = Indexes0.DEPOSIT_RESPONSES_DEPOSIT_RESPONSES_KB_PAYMENT_TRANSACTION_ID;

    // -------------------------------------------------------------------------
    // [#1459] distribute members to avoid static initialisers > 64kb
    // -------------------------------------------------------------------------

    private static class Indexes0 {
        public static Index DEPOSIT_PAYMENT_METHODS_DEPOSIT_PAYMENT_METHODS_KB_PAYMENT_ID = Internal.createIndex("deposit_payment_methods_kb_payment_id", DepositPaymentMethods.DEPOSIT_PAYMENT_METHODS, new OrderField[] { DepositPaymentMethods.DEPOSIT_PAYMENT_METHODS.KB_PAYMENT_METHOD_ID }, true);
        public static Index DEPOSIT_RESPONSES_DEPOSIT_RESPONSES_DEPOSIT_REFERENCE_NUMBER = Internal.createIndex("deposit_responses_deposit_reference_number", DepositResponses.DEPOSIT_RESPONSES, new OrderField[] { DepositResponses.DEPOSIT_RESPONSES.DEPOSIT_REFERENCE_NUMBER }, false);
        public static Index DEPOSIT_RESPONSES_DEPOSIT_RESPONSES_KB_PAYMENT_ID = Internal.createIndex("deposit_responses_kb_payment_id", DepositResponses.DEPOSIT_RESPONSES, new OrderField[] { DepositResponses.DEPOSIT_RESPONSES.KB_PAYMENT_ID }, false);
        public static Index DEPOSIT_RESPONSES_DEPOSIT_RESPONSES_KB_PAYMENT_TRANSACTION_ID = Internal.createIndex("deposit_responses_kb_payment_transaction_id", DepositResponses.DEPOSIT_RESPONSES, new OrderField[] { DepositResponses.DEPOSIT_RESPONSES.KB_PAYMENT_TRANSACTION_ID }, false);
    }
}
