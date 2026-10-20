package com.example.learning.oop.subtyping.model;

/**
 * Nominal Java subtype contracts: sharing a method signature alone does not
 * make LegacyGateway implement PaymentMethod. LegacyPaymentAdapter does.
 */
public final class PaymentMethods {
    private PaymentMethods() {
    }

    public interface PaymentMethod {
        String description();
        String charge(long cents);
    }

    private static void validCents(long cents) {
        if (cents < 1 || cents > 100_000_000L) {
            throw new IllegalArgumentException("amountCents must be between 1 and 100000000");
        }
    }

    public static final class CardPayment implements PaymentMethod {
        private final String lastFourDigits;

        public CardPayment(String lastFourDigits) {
            if (lastFourDigits == null || !lastFourDigits.matches("[0-9]{4}")) {
                throw new IllegalArgumentException("lastFourDigits must contain exactly four digits");
            }
            this.lastFourDigits = lastFourDigits;
        }

        public String lastFourDigits() {
            return lastFourDigits;
        }

        @Override
        public String description() {
            return "CARD-****" + lastFourDigits;
        }

        @Override
        public String charge(long cents) {
            validCents(cents);
            return "CARD:APPROVED:" + cents;
        }
    }

    public static final class WalletPayment implements PaymentMethod {
        @Override
        public String description() {
            return "WALLET";
        }

        @Override
        public String charge(long cents) {
            validCents(cents);
            return "WALLET:APPROVED:" + cents;
        }
    }

    public static final class LegacyGateway {
        public String submitCents(long cents) {
            validCents(cents);
            return "LEGACY:APPROVED:" + cents;
        }
    }

    public static final class LegacyPaymentAdapter implements PaymentMethod {
        private final LegacyGateway gateway;

        public LegacyPaymentAdapter(LegacyGateway gateway) {
            if (gateway == null) {
                throw new IllegalArgumentException("gateway is required");
            }
            this.gateway = gateway;
        }

        @Override
        public String description() {
            return "ADAPTER(LEGACY)";
        }

        @Override
        public String charge(long cents) {
            return gateway.submitCents(cents);
        }
    }

    public static String inspect(PaymentMethod method) {
        return "OVERLOAD:PaymentMethod";
    }

    public static String inspect(CardPayment method) {
        return "OVERLOAD:CardPayment";
    }
}
