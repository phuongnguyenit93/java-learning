package com.example.learning.oop.subtyping;

import static org.junit.jupiter.api.Assertions.*;

import com.example.learning.oop.subtyping.dto.SubtypingDtos.*;
import com.example.learning.oop.subtyping.model.DiscountPolicies;
import com.example.learning.oop.subtyping.model.PaymentMethods;
import com.example.learning.oop.subtyping.service.SubtypingExperimentService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubtypingExperimentServiceTest {
    private final SubtypingExperimentService service = new SubtypingExperimentService();

    @Test
    void virtualDispatchAndStaticOverloadSelectionDiffer() {
        DispatchResult result = service.dispatch(new DispatchRequest(PaymentImplementation.CARD, 4500L));
        assertEquals("PaymentMethod", result.declaredType());
        assertEquals("CardPayment", result.runtimeType());
        assertTrue(result.sameReferenceAfterUpcast());
        assertEquals("CARD-****1234", result.description());
        assertEquals("CARD:APPROVED:4500", result.receipt());
        assertEquals("OVERLOAD:PaymentMethod", result.overloadForBaseReference());
        assertEquals("OVERLOAD:CardPayment", result.overloadForConcreteReference());
        assertTrue(result.cardSpecificCapabilityAvailable());
        assertEquals("1234", result.cardLastFourDigits());
    }

    @Test
    void walletCannotBeDowncastToCardAndHasNoCardSpecificCapability() {
        DispatchResult wallet = service.dispatch(new DispatchRequest(PaymentImplementation.WALLET, 7L));
        assertEquals("WalletPayment", wallet.runtimeType());
        assertEquals("WALLET:APPROVED:7", wallet.receipt());
        assertFalse(wallet.cardSpecificCapabilityAvailable());
        assertNull(wallet.cardLastFourDigits());
        assertNull(wallet.overloadForConcreteReference());
        PaymentMethods.PaymentMethod ref = new PaymentMethods.WalletPayment();
        assertFalse(ref instanceof PaymentMethods.CardPayment);
        assertThrows(ClassCastException.class, () -> {
            PaymentMethods.CardPayment ignored = (PaymentMethods.CardPayment) ref;
        });
    }

    @Test
    void legacyAdapterIsNominalSubtypeAndUsesDelegation() {
        DispatchResult legacy = service.dispatch(new DispatchRequest(PaymentImplementation.LEGACY_ADAPTER, 120L));
        assertEquals("LegacyPaymentAdapter", legacy.runtimeType());
        assertTrue(legacy.legacyUsesAdapter());
        assertEquals("LEGACY:APPROVED:120", legacy.receipt());
        assertEquals("OVERLOAD:PaymentMethod", legacy.overloadForBaseReference());
        assertTrue(legacy.sameReferenceAfterUpcast());
        assertThrows(IllegalArgumentException.class, () ->
                service.dispatch(new DispatchRequest(PaymentImplementation.CARD, -1L)));
    }

    @Test
    void validDiscountImplementationsRespectAllPricesIncludingBoundary() {
        DiscountResult fixed = service.discountContract(
                new DiscountRequest(DiscountImplementation.FIXED, List.of(0L, 50L, 100L),
                        60L, null, null));
        assertTrue(fixed.allCasesPass());
        assertEquals(List.of(0L, 0L, 40L),
                fixed.cases().stream().map(DiscountCase::netCents).toList());
        DiscountResult percent = service.discountContract(
                new DiscountRequest(DiscountImplementation.PERCENT, List.of(0L, 7L, 100_000_000L),
                        null, 25, null));
        assertTrue(percent.allCasesPass());
        assertEquals(List.of(0L, 6L, 75_000_000L),
                percent.cases().stream().map(DiscountCase::netCents).toList());
        assertEquals(0L, new DiscountPolicies.PercentDiscount(100).netCents(1));
    }

    @Test
    void nominalDiscountSubtypeCanStrengthenPreconditionAndFailLsp() {
        DiscountResult result = service.discountContract(
                new DiscountRequest(DiscountImplementation.STRICT_MINIMUM,
                        List.of(0L, 49L, 50L, 100L), null, null, 50L));
        assertFalse(result.allCasesPass());
        assertEquals("DiscountPolicy", result.declaredType());
        assertEquals("StrictMinimumDiscount", result.runtimeType());
        assertFalse(result.cases().get(0).satisfiedContract());
        assertNull(result.cases().get(1).netCents());
        assertTrue(result.cases().get(1).failure().contains("Precondition violated"));
        assertTrue(result.cases().get(2).satisfiedContract());
        assertEquals(100L, result.cases().get(3).netCents());
    }

    @Test
    void discountInputBoundsAndMissingParametersFailWithoutPartialEffects() {
        assertThrows(IllegalArgumentException.class, () -> service.discountContract(
                new DiscountRequest(DiscountImplementation.FIXED, List.of(-1L), 5L, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.discountContract(
                new DiscountRequest(DiscountImplementation.FIXED, List.of(10L), null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.discountContract(
                new DiscountRequest(DiscountImplementation.PERCENT, List.of(10L), null, 101, null)));
        assertThrows(IllegalArgumentException.class, () -> service.discountContract(
                new DiscountRequest(DiscountImplementation.PERCENT, List.of(), null, 10, null)));
    }

    @Test
    void rectangleKeepsIndependentSetterContractButSquareBreaksIt() {
        GeometryResult rectangle = service.geometryContract(new GeometryRequest(
                MutableKind.RECTANGLE, 2, 2, 5, 3));
        GeometryResult square = service.geometryContract(new GeometryRequest(
                MutableKind.SQUARE_AS_RECTANGLE, 2, 2, 5, 3));
        assertEquals("MutableRectangle", rectangle.declaredType());
        assertTrue(rectangle.rectangleContractRespected());
        assertTrue(rectangle.widthSetterKeptHeight());
        assertTrue(rectangle.heightSetterKeptWidth());
        assertEquals(15L, rectangle.finalArea());
        assertEquals("MutableSquare", square.runtimeType());
        assertFalse(square.rectangleContractRespected());
        assertFalse(square.widthSetterKeptHeight());
        assertFalse(square.heightSetterKeptWidth());
        assertEquals(9L, square.finalArea());
        assertEquals(15L, square.expectedRectangleArea());
        assertEquals(15L, square.immutableRectangleArea());
        assertEquals(9L, square.immutableSquareArea());
        assertEquals("MeasurableShape", square.immutableInterface());
    }

    @Test
    void shapeValidationKeepsMathWithinLongAndSquareInvariant() {
        assertThrows(IllegalArgumentException.class, () -> service.geometryContract(
                new GeometryRequest(MutableKind.SQUARE_AS_RECTANGLE, 2, 3, 5, 4)));
        assertThrows(IllegalArgumentException.class, () -> service.geometryContract(
                new GeometryRequest(MutableKind.RECTANGLE, 0, 3, 5, 4)));
        GeometryResult huge = service.geometryContract(
                new GeometryRequest(MutableKind.RECTANGLE, 1, 1, 10000, 10000));
        assertEquals(100_000_000L, huge.finalArea());
    }
}
