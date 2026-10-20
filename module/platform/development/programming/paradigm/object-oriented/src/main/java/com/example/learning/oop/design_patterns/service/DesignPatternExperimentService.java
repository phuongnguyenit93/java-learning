package com.example.learning.oop.design_patterns.service;

import com.example.learning.oop.design_patterns.model.PricingPipeline;
import com.example.learning.oop.design_patterns.model.TaskBoard;
import com.example.learning.oop.design_patterns.model.TaskCommandManager;
import com.example.learning.oop.design_patterns.model.ReportPatterns;
import static com.example.learning.oop.design_patterns.dto.DesignPatternDtos.*;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Per-request, bounded simulations; no shared mutable state, remote I/O or persistence. */
@Service
public final class DesignPatternExperimentService {
    private static final String SCOPE = "REQUEST_LOCAL_IN_MEMORY_NO_EXTERNAL_SIDE_EFFECTS";

    public PricingResult price(PricingRequest request) {
        if (request == null || request.subtotalVnd() == null ||
                request.deliveryFeeVnd() == null || request.order() == null) {
            throw new IllegalArgumentException("subtotalVnd, deliveryFeeVnd, strategy and order required");
        }
        var discountFirst = option(request, "DISCOUNT_THEN_FEE");
        var feeFirst = option(request, "FEE_THEN_DISCOUNT");
        PricingOption selected = switch (request.order()) {
            case "DISCOUNT_THEN_FEE" -> discountFirst;
            case "FEE_THEN_DISCOUNT" -> feeFirst;
            default -> throw new IllegalArgumentException("unsupported pricing order");
        };
        return new PricingResult(request.strategy(), request.subtotalVnd(), request.deliveryFeeVnd(),
                selected, List.of(discountFirst, feeFirst), SCOPE);
    }

    public TasksResult tasks(TasksRequest request) {
        if (request == null || request.actions() == null ||
                request.actions().isEmpty() || request.actions().size() > 20) {
            throw new IllegalArgumentException("task actions must have 1..20 entries");
        }
        for (TaskAction action : request.actions()) {
            if (action == null || action.type() == null ||
                    !List.of("ADD", "COMPLETE", "UNDO", "REDO", "UNSUBSCRIBE_NOTIFY").contains(action.type())) {
                throw new IllegalArgumentException("task type must be ADD/COMPLETE/UNDO/REDO/UNSUBSCRIBE_NOTIFY");
            }
            if (action.type().equals("ADD") && (action.id() == null || action.title() == null)) {
                throw new IllegalArgumentException("ADD needs id and title");
            }
            if (action.type().equals("COMPLETE") && action.id() == null) {
                throw new IllegalArgumentException("COMPLETE needs id");
            }
        }
        TaskBoard board = new TaskBoard();
        TaskCommandManager.Events events = new TaskCommandManager.Events();
        // Both observers consume actual published events; only one can be unsubscribed.
        events.subscribe("AUDIT", ignored -> {});
        events.subscribe("NOTIFY", ignored -> {});
        TaskCommandManager manager = new TaskCommandManager(events);
        TaskSnapshot initial = snapshot(board, manager, events);
        List<TaskStep> steps = new ArrayList<>();
        int index = 1;
        for (TaskAction action : request.actions()) {
            TaskSnapshot before = snapshot(board, manager, events);
            boolean accepted = true;
            String detail = "completed";
            try {
                switch (action.type()) {
                    case "ADD" -> manager.execute(new TaskCommandManager.AddTask(board, action.id(), action.title()));
                    case "COMPLETE" -> manager.execute(new TaskCommandManager.CompleteTask(board, action.id()));
                    case "UNDO" -> manager.undo();
                    case "REDO" -> manager.redo();
                    case "UNSUBSCRIBE_NOTIFY" -> events.unsubscribe("NOTIFY");
                    default -> throw new IllegalArgumentException("unsupported task action");
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                accepted = false;
                detail = exception.getMessage();
            }
            steps.add(new TaskStep(index++, action.type(), accepted, detail,
                    before, snapshot(board, manager, events)));
        }
        return new TasksResult(initial, List.copyOf(steps), snapshot(board, manager, events), SCOPE);
    }

    public ReportResult report(ReportRequest request) {
        if (request == null || request.format() == null || request.sales() == null ||
                request.sales().isEmpty() || request.sales().size() > 20) {
            throw new IllegalArgumentException("format and 1..20 sales are required");
        }
        List<ReportPatterns.Sale> sales = new ArrayList<>();
        for (SaleInput sale : request.sales()) {
            if (sale == null || sale.amountVnd() == null) {
                throw new IllegalArgumentException("sale id and amountVnd are required");
            }
            sales.add(new ReportPatterns.Sale(sale.id(), sale.amountVnd()));
        }
        ReportPatterns.ReportCreator creator = switch (request.format()) {
            case "TEXT" -> new ReportPatterns.TextCreator();
            case "CSV" -> new ReportPatterns.CsvCreator();
            default -> throw new IllegalArgumentException("format must be TEXT or CSV");
        };
        var result = creator.create(sales);
        return new ReportResult(request.format(), result.content(), result.totalVnd(),
                result.count(), result.executionTrace());
    }

    private static PricingOption option(PricingRequest request, String order) {
        PricingPipeline.Quote result = PricingPipeline.calculate(
                request.subtotalVnd(), request.deliveryFeeVnd(), request.strategy(), order);
        return new PricingOption(order, result.payableVnd(), result.discountVnd(),
                result.executionTrace());
    }

    private static TaskSnapshot snapshot(TaskBoard board, TaskCommandManager manager,
                                         TaskCommandManager.Events events) {
        List<TaskView> tasks = board.snapshot().stream()
                .map(t -> new TaskView(t.id(), t.title(), t.done())).toList();
        return new TaskSnapshot(tasks, manager.undoDepth(), manager.redoDepth(), events.deliveries());
    }
}
