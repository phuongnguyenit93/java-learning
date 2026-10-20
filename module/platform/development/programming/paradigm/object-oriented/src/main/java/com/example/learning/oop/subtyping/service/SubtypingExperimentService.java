package com.example.learning.oop.subtyping.service;

import com.example.learning.oop.subtyping.dto.SubtypingDtos.*;
import com.example.learning.oop.subtyping.model.DiscountPolicies;
import com.example.learning.oop.subtyping.model.DiscountPolicies.DiscountPolicy;
import com.example.learning.oop.subtyping.model.GeometryModels;
import com.example.learning.oop.subtyping.model.GeometryModels.MeasurableShape;
import com.example.learning.oop.subtyping.model.GeometryModels.MutableRectangle;
import com.example.learning.oop.subtyping.model.GeometryModels.MutableSquare;
import com.example.learning.oop.subtyping.model.PaymentMethods;
import com.example.learning.oop.subtyping.model.PaymentMethods.CardPayment;
import com.example.learning.oop.subtyping.model.PaymentMethods.LegacyGateway;
import com.example.learning.oop.subtyping.model.PaymentMethods.LegacyPaymentAdapter;
import com.example.learning.oop.subtyping.model.PaymentMethods.PaymentMethod;
import com.example.learning.oop.subtyping.model.PaymentMethods.WalletPayment;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Per-request experiments: no external billing, shared mutable state or persistence. */
@Service
public final class SubtypingExperimentService {

    public DispatchResult dispatch(DispatchRequest request) {
        if (request == null || request.implementation() == null || request.amountCents() == null) {
            throw new IllegalArgumentException("implementation and amountCents are required");
        }
        long amount = request.amountCents();
        if (amount < 1 || amount > 100_000_000L) {
            throw new IllegalArgumentException("amountCents must be between 1 and 100000000");
        }

        PaymentMethod payment = switch (request.implementation()) {
            case CARD -> new CardPayment("1234");
            case WALLET -> new WalletPayment();
            case LEGACY_ADAPTER -> new LegacyPaymentAdapter(new LegacyGateway());
        };
        // Explicit alias demonstrates upcast: no cloning or new object is introduced.
        PaymentMethod upcastReference = payment;
        boolean card = payment instanceof CardPayment;
        String digits = payment instanceof CardPayment cardPayment
                ? cardPayment.lastFourDigits() : null;
        String specificOverload = payment instanceof CardPayment cardPayment
                ? PaymentMethods.inspect(cardPayment) : null;
        return new DispatchResult(
                PaymentMethod.class.getSimpleName(), payment.getClass().getSimpleName(),
                payment == upcastReference, payment.description(), payment.charge(amount),
                PaymentMethods.inspect(upcastReference), specificOverload,
                card, digits, payment instanceof LegacyPaymentAdapter);
    }

    public DiscountResult discountContract(DiscountRequest request) {
        if (request == null || request.implementation() == null || request.pricesCents() == null
                || request.pricesCents().isEmpty() || request.pricesCents().size() > 20) {
            throw new IllegalArgumentException("implementation and 1-20 pricesCents are required");
        }
        for (Long price : request.pricesCents()) {
            if (price == null) {
                throw new IllegalArgumentException("pricesCents cannot contain null");
            }
            DiscountPolicies.requirePrice(price);
        }
        DiscountPolicy policy = switch (request.implementation()) {
            case FIXED -> new DiscountPolicies.FixedDiscount(require(request.discountCents(), "discountCents"));
            case PERCENT -> new DiscountPolicies.PercentDiscount(require(request.percent(), "percent"));
            case STRICT_MINIMUM -> new DiscountPolicies.StrictMinimumDiscount(
                    require(request.minimumCents(), "minimumCents"));
        };
        List<DiscountCase> cases = new ArrayList<>();
        boolean allPass = true;
        for (Long price : request.pricesCents()) {
            Long actual = null;
            String failure = null;
            try {
                actual = policy.netCents(price);
                if (actual < 0 || actual > price) {
                    failure = "Postcondition violated: netCents outside [0, original price]";
                }
            } catch (IllegalArgumentException ex) {
                failure = "Precondition violated: valid supertype input rejected: " + ex.getMessage();
            }
            boolean satisfies = failure == null;
            allPass &= satisfies;
            cases.add(new DiscountCase(price, actual, satisfies, failure));
        }
        return new DiscountResult(DiscountPolicy.class.getSimpleName(),
                policy.getClass().getSimpleName(), allPass, List.copyOf(cases));
    }

    public GeometryResult geometryContract(GeometryRequest request) {
        if (request == null || request.kind() == null || request.initialWidth() == null
                || request.initialHeight() == null || request.newWidth() == null
                || request.newHeight() == null) {
            throw new IllegalArgumentException("kind and all dimensions are required");
        }
        int initialWidth = GeometryModels.requireDimension(request.initialWidth());
        int initialHeight = GeometryModels.requireDimension(request.initialHeight());
        int newWidth = GeometryModels.requireDimension(request.newWidth());
        int newHeight = GeometryModels.requireDimension(request.newHeight());
        if (request.kind() == MutableKind.SQUARE_AS_RECTANGLE && initialWidth != initialHeight) {
            throw new IllegalArgumentException("Square initial width and height must be equal");
        }
        MutableRectangle rectangle = request.kind() == MutableKind.RECTANGLE
                ? new MutableRectangle(initialWidth, initialHeight) : new MutableSquare(initialWidth);
        int beforeWidth = rectangle.width();
        int beforeHeight = rectangle.height();
        rectangle.setWidth(newWidth);
        boolean widthKeptHeight = rectangle.height() == beforeHeight && rectangle.width() == newWidth;
        int widthBeforeHeightChange = rectangle.width();
        rectangle.setHeight(newHeight);
        boolean heightKeptWidth = rectangle.width() == widthBeforeHeightChange
                && rectangle.height() == newHeight;
        MeasurableShape immutableRectangle = new GeometryModels.ImmutableRectangle(newWidth, newHeight);
        MeasurableShape immutableSquare = new GeometryModels.ImmutableSquare(newHeight);
        return new GeometryResult(
                MutableRectangle.class.getSimpleName(), rectangle.getClass().getSimpleName(),
                rectangle.width(), rectangle.height(), rectangle.area(),
                widthKeptHeight, heightKeptWidth,
                widthKeptHeight && heightKeptWidth,
                (long) newWidth * newHeight,
                immutableRectangle.area(), immutableSquare.area(),
                MeasurableShape.class.getSimpleName());
    }

    private static long require(Long value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required for the selected implementation");
        }
        return value;
    }

    private static int require(Integer value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required for the selected implementation");
        }
        return value;
    }
}
