# killbill-deposit-plugin
![Maven Central](https://img.shields.io/maven-central/v/org.kill-bill.billing.plugin.java/deposit-plugin?color=blue&label=Maven%20Central)

Kill Bill Deposit plugin.

## Kill Bill compatibility

| Plugin version | Kill Bill version |
|---------------:|------------------:|
|          0.y.z |            0.22.z |
|          0.1.z |            0.24.z |

## Requirements

The plugin needs a database. The latest version of the schema can be found [here](https://github.com/killbill/killbill-deposit-plugin/blob/master/src/main/resources/ddl.sql).

## Installation

Locally:

```
kpm install_java_plugin deposit --from-source-file target/deposit-*-SNAPSHOT.jar --destination /var/tmp/bundles
```

## Configuration

Configure minimum payment amounts in the plugin:

```
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d '!!org.killbill.billing.plugin.deposit.DepositConfiguration
  minAmounts:
    USD: 0.5' \
    http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-deposit
```

Make sure to enable the control plugin in Kill Bill:

```
org.killbill.payment.invoice.plugin=killbill-deposit
```

This can also be configured on a per-tenant or even per-request basis.

## Usage

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H "X-Killbill-ApiKey: bob" \
     -H "X-Killbill-ApiSecret: lazar" \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -H "X-Killbill-CreatedBy: testing" \
     -d '{
  "accountId": "e4ab98c2-3a4c-4595-ac14-b70e7324b1b5",
  "effectiveDate": "2021-03-16",
  "paymentReferenceNumber": "WIRE-12345",
  "depositType": "wire",
  "payments": [
    {
      "invoiceNumber": 824,
      "paymentAmount": 1.23
    }
  ]
}' \
     "http://127.0.0.1:8080/plugins/killbill-deposit/record"
```

## About

Kill Bill is the leading Open-Source Subscription Billing & Payments Platform. For more information about the project, go to https://killbill.io/.
