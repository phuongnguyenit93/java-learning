package com.example.learning.oop.language_mechanics.service;

import com.example.learning.oop.language_mechanics.model.ProductKey;
import com.example.learning.oop.language_mechanics.model.StockItem;
import com.example.learning.oop.language_mechanics.repository.StockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import static com.example.learning.oop.language_mechanics.dto.LanguageMechanicsDtos.*;

@Service
public final class StockExperimentService {
    public StockResult execute(StockRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.actions() == null || request.actions().isEmpty() || request.actions().size() > 60) {
            throw new IllegalArgumentException("Provide 1..60 stock actions");
        }
        StockRegistry registry = new StockRegistry();
        List<StockStep> steps = new ArrayList<>();
        for (int i = 0; i < request.actions().size(); i++) {
            StockAction action = Objects.requireNonNull(request.actions().get(i), "action");
            if (action.type() == null) {
                throw new IllegalArgumentException("Action type is required");
            }
            ProductKey key = new ProductKey(action.tenantId(), action.sku());
            StockItem item = registry.find(key);
            StockStep step;
            switch (action.type()) {
                case "REGISTER" -> {
                    int quantity = requireQuantity(action.quantity(), false);
                    if (item != null) {
                        step = new StockStep(i + 1, "REGISTER", false, "ALREADY_REGISTERED",
                                item.available(), item.toString());
                    } else {
                        StockItem created = new StockItem(key, quantity,
                                action.contactEmail(), action.providerToken());
                        registry.register(key, created);
                        step = new StockStep(i + 1, "REGISTER", true, "REGISTERED",
                                created.available(), created.toString());
                    }
                }
                case "READ" -> step = status(i, "READ", item);
                case "RESTOCK" -> {
                    int quantity = requireQuantity(action.quantity(), true);
                    if (item == null) {
                        step = status(i, "RESTOCK", null);
                    } else {
                        item.restock(quantity);
                        step = new StockStep(i + 1, "RESTOCK", true, "RESTOCKED",
                                item.available(), item.toString());
                    }
                }
                case "RESERVE" -> {
                    int quantity = requireQuantity(action.quantity(), true);
                    if (item == null) {
                        step = status(i, "RESERVE", null);
                    } else {
                        var outcome = item.reserve(quantity);
                        if (outcome instanceof StockItem.ReserveDeclined rejected) {
                            step = new StockStep(i + 1, "RESERVE", false, rejected.code(),
                                    rejected.remaining(), item.toString());
                        } else {
                            step = new StockStep(i + 1, "RESERVE", true, "RESERVED",
                                    item.available(), item.toString());
                        }
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported stock action");
            }
            steps.add(step);
        }
        return new StockResult(List.copyOf(steps), registry.size());
    }

    private static StockStep status(int index, String action, StockItem item) {
        if (item == null) {
            return new StockStep(index + 1, action, false, "PRODUCT_NOT_FOUND", null, null);
        }
        return new StockStep(index + 1, action, true, "FOUND", item.available(), item.toString());
    }

    private static int requireQuantity(Integer quantity, boolean positive) {
        if (quantity == null || quantity < (positive ? 1 : 0)) {
            throw new IllegalArgumentException("Invalid stock action quantity");
        }
        return quantity;
    }
}
