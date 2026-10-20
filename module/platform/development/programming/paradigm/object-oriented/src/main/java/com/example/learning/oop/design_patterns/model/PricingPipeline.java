package com.example.learning.oop.design_patterns.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy changes an actual discount algorithm; Decorator changes the order
 * in which independent processing stages are applied.
 */
public final class PricingPipeline {
    private PricingPipeline() {}

    public record Quote(long payableVnd, long discountVnd, long deliveryFeeVnd,
                        List<String> executionTrace) {}

    private interface DiscountStrategy {
        long discount(long subtotalVnd);
        String code();
    }

    private static final class NoDiscount implements DiscountStrategy {
        @Override public long discount(long subtotal) { return 0L; }
        @Override public String code() { return "NONE"; }
    }

    private static final class MemberDiscount implements DiscountStrategy {
        @Override public long discount(long subtotal) {
            // round HALF_UP to whole VND: floor((amount * 5 + 50) / 100)
            return Math.addExact(Math.multiplyExact(subtotal, 5L), 50L) / 100L;
        }
        @Override public String code() { return "MEMBER_5"; }
    }

    private static final class VoucherDiscount implements DiscountStrategy {
        @Override public long discount(long subtotal) { return Math.min(30_000L, subtotal); }
        @Override public String code() { return "VOUCHER_30000"; }
    }

    private interface PriceStage {
        long apply(List<String> trace);
    }

    private record BasePrice(long subtotal) implements PriceStage {
        @Override public long apply(List<String> trace) {
            trace.add("BASE:" + subtotal);
            return subtotal;
        }
    }

    private record DiscountDecorator(PriceStage inner, DiscountStrategy strategy) implements PriceStage {
        @Override public long apply(List<String> trace) {
            long before = inner.apply(trace);
            long discount = strategy.discount(before);
            if (discount < 0 || discount > before) {
                throw new IllegalStateException("discount strategy violated the payable contract");
            }
            long result = before - discount;
            trace.add("DISCOUNT_" + strategy.code() + ":" + discount);
            return result;
        }
    }

    private record DeliveryDecorator(PriceStage inner, long fee) implements PriceStage {
        @Override public long apply(List<String> trace) {
            long before = inner.apply(trace);
            long result = Math.addExact(before, fee);
            trace.add("DELIVERY:" + fee);
            return result;
        }
    }

    public static Quote calculate(long subtotal, long deliveryFee, String strategyCode,
                                  String order) {
        if (subtotal < 0 || subtotal > 1_000_000_000_000L) {
            throw new IllegalArgumentException("subtotalVnd must be 0..1000000000000");
        }
        if (deliveryFee < 0 || deliveryFee > 1_000_000L) {
            throw new IllegalArgumentException("deliveryFeeVnd must be 0..1000000");
        }
        DiscountStrategy strategy = switch (strategyCode == null ? "" : strategyCode) {
            case "NONE" -> new NoDiscount();
            case "MEMBER_5" -> new MemberDiscount();
            case "VOUCHER_30000" -> new VoucherDiscount();
            default -> throw new IllegalArgumentException("strategy must be NONE/MEMBER_5/VOUCHER_30000");
        };
        PriceStage base = new BasePrice(subtotal);
        PriceStage pipe = switch (order == null ? "" : order) {
            case "DISCOUNT_THEN_FEE" -> new DeliveryDecorator(
                    new DiscountDecorator(base, strategy), deliveryFee);
            case "FEE_THEN_DISCOUNT" -> new DiscountDecorator(
                    new DeliveryDecorator(base, deliveryFee), strategy);
            default -> throw new IllegalArgumentException("order must be DISCOUNT_THEN_FEE or FEE_THEN_DISCOUNT");
        };
        List<String> trace = new ArrayList<>();
        long payable = pipe.apply(trace);
        if (payable < 0) throw new IllegalStateException("payable cannot be negative");
        return new Quote(payable, Math.addExact(subtotal, deliveryFee) - payable,
                deliveryFee, List.copyOf(trace));
    }
}
