package com.example.learning.oop.language_mechanics;

import com.example.learning.oop.language_mechanics.model.ProductKey;
import com.example.learning.oop.language_mechanics.model.StockItem;
import com.example.learning.oop.language_mechanics.repository.StockRegistry;
import com.example.learning.oop.language_mechanics.service.StockExperimentService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.example.learning.oop.language_mechanics.dto.LanguageMechanicsDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class StockContractsTest {
    private static StockAction action(String type, String tenant, String sku, Integer qty) {
        return new StockAction(type, tenant, sku, qty, null, null);
    }

    @Test
    void immutableCompositeKeysObeyEqualsHashAndKeepTenantsSeparate() {
        ProductKey first = new ProductKey("tenantA", "JAVA");
        ProductKey copy = new ProductKey("tenantA", "JAVA");
        ProductKey otherTenant = new ProductKey("tenantB", "JAVA");
        assertNotSame(first, copy);
        assertEquals(first, copy);
        assertEquals(copy, first);
        assertEquals(first.hashCode(), copy.hashCode());
        assertNotEquals(first, otherTenant);
        assertEquals(2, new HashSet<>(List.of(first, copy, otherTenant)).size());
        HashMap<ProductKey, String> products = new HashMap<>();
        products.put(first, "item");
        assertEquals("item", products.get(copy));
        assertThrows(IllegalArgumentException.class, () -> new ProductKey("bad\nid", "JAVA"));
    }

    @Test
    void stockRegistryProtectsStateAndToStringDoesNotExposePrivateFields() {
        StockItem item = new StockItem(new ProductKey("T", "SKU"), 5,
                "hidden@example.com", "top-secret-token");
        StockRegistry registry = new StockRegistry();
        assertTrue(registry.register(new ProductKey("T", "SKU"), item));
        assertSame(item, registry.find(new ProductKey("T", "SKU")));
        assertEquals(5, registry.find(new ProductKey("T", "SKU")).available());
        assertFalse(registry.register(new ProductKey("T", "SKU"), item));
        assertFalse(item.toString().contains("hidden@example.com"));
        assertFalse(item.toString().contains("top-secret-token"));
        assertTrue(item.toString().contains("available=5"));
        var refused = item.reserve(6);
        assertInstanceOf(StockItem.ReserveDeclined.class, refused);
        assertEquals(5, item.available());
        assertInstanceOf(StockItem.ReserveAccepted.class, item.reserve(3));
        assertEquals(2, item.available());
        assertThrows(IllegalArgumentException.class, () -> item.reserve(0));
        assertEquals(2, item.available());
        assertThrows(ArithmeticException.class, () -> item.restock(Integer.MAX_VALUE));
        assertEquals(2, item.available());
    }

    @Test
    void scenarioExposesBusinessRefusalsAndReadOnlySnapshots() {
        StockResult actual = new StockExperimentService().execute(new StockRequest(List.of(
                new StockAction("REGISTER", "T", "SKU", 4,
                        "private@example.org", "secret-value"),
                action("READ", "T", "SKU", null),
                action("REGISTER", "T", "SKU", 99),
                action("RESERVE", "T", "SKU", 7),
                action("RESERVE", "T", "SKU", 3),
                action("RESTOCK", "T", "SKU", 5),
                action("READ", "T", "SKU", null),
                action("READ", "OTHER", "SKU", null))));
        assertEquals(1, actual.uniqueProducts());
        assertEquals(List.of("REGISTERED", "FOUND", "ALREADY_REGISTERED",
                        "INSUFFICIENT_STOCK", "RESERVED", "RESTOCKED",
                        "FOUND", "PRODUCT_NOT_FOUND"),
                actual.steps().stream().map(StockStep::code).toList());
        assertEquals(4, actual.steps().get(3).available());
        assertEquals(1, actual.steps().get(4).available());
        assertEquals(6, actual.steps().get(6).available());
        assertEquals(4, actual.steps().get(1).available());
        assertTrue(actual.steps().stream().map(StockStep::diagnostic)
                .filter(java.util.Objects::nonNull).noneMatch(s -> s.contains("secret-value")));
    }

    @Test
    void malformedCommandsRejectWithoutReportingSuccess() {
        var service = new StockExperimentService();
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new StockRequest(List.of(action("REGISTER", "T", "SKU", -1)))));
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new StockRequest(List.of(action("RESERVE", "T", "SKU", 0)))));
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new StockRequest(List.of(action("REMOVE_ALL", "T", "SKU", 1)))));
    }
}
