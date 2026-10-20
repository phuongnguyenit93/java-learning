package com.example.learning.oop.interface_chapter.service;

import com.example.learning.oop.interface_chapter.model.*;
import static com.example.learning.oop.interface_chapter.dto.InterfaceExperimentDtos.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

/** All experiments create request-local objects; there is no shared or remote state. */
@Service
public final class InterfaceExperimentsService {
    private static final String SCOPE = "REQUEST_LOCAL_IN_MEMORY_SINGLE_THREAD_NO_EXTERNAL_SIDE_EFFECTS";
    private static final int MAX_ACTIONS = 20;

    public ShippingResult shipping(ShippingRequest request) {
        if (request == null || request.distanceKm() == null || request.orderValueVnd() == null
                || request.selectedPolicy() == null) {
            throw new IllegalArgumentException("distanceKm, orderValueVnd and selectedPolicy are required");
        }
        ShippingFeePolicy.requireValid(request.distanceKm(), request.orderValueVnd());
        var economy = new ShippingQuoteService(new EconomyShippingPolicy());
        var express = new ShippingQuoteService(new ExpressShippingPolicy());
        var prices = List.of(new ShippingPrice(economy.policyCode(),
                        economy.quote(request.distanceKm(), request.orderValueVnd())),
                new ShippingPrice(express.policyCode(),
                        express.quote(request.distanceKm(), request.orderValueVnd())));
        ShippingPrice selected = prices.stream()
                .filter(p -> p.policy().equals(request.selectedPolicy()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "selectedPolicy must be ECONOMY or EXPRESS"));
        return new ShippingResult(selected, prices, prices.get(0).feeVnd() == 0);
    }

    public DocumentResult documents(DocumentRequest request) {
        if (request == null || request.initialDocuments() == null
                || request.initialDocuments().size() > 15) {
            throw new IllegalArgumentException("initialDocuments must contain 0..15 entries");
        }
        validateActions(request.actions());
        var distinct = new HashSet<String>();
        for (DocumentInput doc : request.initialDocuments()) {
            if (doc == null) throw new IllegalArgumentException("initial document cannot be null");
            new DocumentEntry(doc.id(), doc.title(), doc.content(), DocumentEntry.Status.DRAFT);
            if (!distinct.add(doc.id())) throw new IllegalArgumentException("initial document ids must be unique");
        }
        for (DocumentAction action : request.actions()) {
            if (action == null || !List.of("save", "read", "publish").contains(action.type())
                    || action.id() == null) {
                throw new IllegalArgumentException("document action must have type save/read/publish and id");
            }
        }

        DocumentWorkspace workspace = new DocumentWorkspace();
        DocumentReader reader = workspace;
        DocumentWriter writer = workspace;
        DocumentPublisher publisher = workspace;
        for (DocumentInput doc : request.initialDocuments()) {
            writer.save(doc.id(), doc.title(), doc.content());
        }
        List<DocumentState> initial = documentSnapshot(reader);
        List<DocumentStep> steps = new ArrayList<>();
        int index = 1;
        for (DocumentAction action : request.actions()) {
            List<DocumentState> before = documentSnapshot(reader);
            DocumentState readResult = null;
            boolean accepted = true;
            String detail = "completed";
            try {
                switch (action.type()) {
                    case "save" -> writer.save(action.id(), action.title(), action.content());
                    case "publish" -> publisher.publish(action.id());
                    case "read" -> {
                        var found = reader.find(action.id());
                        readResult = found.map(InterfaceExperimentsService::documentState).orElse(null);
                        detail = found.isPresent() ? "found" : "not found";
                    }
                    default -> throw new IllegalArgumentException("unsupported document action");
                }
            } catch (IllegalArgumentException | IllegalStateException error) {
                accepted = false;
                detail = error.getMessage();
            }
            steps.add(new DocumentStep(index++, action.type(), action.id(), accepted, detail,
                    before, documentSnapshot(reader), readResult));
        }
        return new DocumentResult(initial, List.copyOf(steps), documentSnapshot(reader),
                reader == writer && writer == publisher, workspace.roleName(), SCOPE);
    }

    public CheckoutResult checkout(CheckoutRequest request) {
        if (request == null || request.gateway() == null || request.repository() == null) {
            throw new IllegalArgumentException("gateway and repository are required");
        }
        validateActions(request.actions());
        PaymentGateway gateway = switch (request.gateway()) {
            case "APPROVE" -> new ApprovalPaymentGateway();
            case "DECLINE" -> new DeclinePaymentGateway();
            default -> throw new IllegalArgumentException("gateway must be APPROVE or DECLINE");
        };
        PaymentRepository repository = switch (request.repository()) {
            case "HASH_MAP" -> new HashPaymentRepository();
            case "TREE_MAP" -> new TreePaymentRepository();
            default -> throw new IllegalArgumentException("repository must be HASH_MAP or TREE_MAP");
        };
        List<PaymentCommand> actions = new ArrayList<>();
        for (PaymentInput a : request.actions()) {
            if (a == null || a.amountVnd() == null) {
                throw new IllegalArgumentException("payment actions need requestId, orderId and amountVnd");
            }
            actions.add(new PaymentCommand(a.requestId(), a.orderId(), a.amountVnd()));
        }

        CheckoutService service = new CheckoutService(gateway, repository);
        CheckoutState initial = checkoutSnapshot(service);
        List<CheckoutStep> steps = new ArrayList<>();
        int index = 1;
        for (PaymentCommand action : actions) {
            CheckoutState before = checkoutSnapshot(service);
            boolean accepted = true, replayed = false;
            String detail = "processed";
            ReceiptState receipt = null;
            try {
                var processed = service.chargeOrReplay(action);
                replayed = processed.replayed();
                detail = replayed ? "replayed without charging" : "gateway processed";
                receipt = receiptState(processed.receipt());
            } catch (IllegalStateException ex) {
                accepted = false;
                detail = ex.getMessage();
            }
            steps.add(new CheckoutStep(index++, action.requestId(), accepted, replayed,
                    detail, receipt, before, checkoutSnapshot(service)));
        }
        return new CheckoutResult(request.gateway(), request.repository(), initial,
                List.copyOf(steps), checkoutSnapshot(service),
                "FAKE_PAYMENT_ONLY_REQUEST_LOCAL_NO_REAL_CHARGE_NO_DURABILITY_OR_CONCURRENT_IDEMPOTENCY");
    }

    private static void validateActions(List<?> actions) {
        if (actions == null || actions.isEmpty() || actions.size() > MAX_ACTIONS) {
            throw new IllegalArgumentException("actions must contain 1..20 entries");
        }
    }

    private static DocumentState documentState(DocumentEntry entry) {
        return new DocumentState(entry.id(), entry.title(), entry.content(), entry.status().name());
    }

    private static List<DocumentState> documentSnapshot(DocumentReader reader) {
        return reader.list().stream().map(InterfaceExperimentsService::documentState).toList();
    }

    private static ReceiptState receiptState(PaymentReceipt receipt) {
        return new ReceiptState(receipt.requestId(), receipt.orderId(),
                receipt.amountVnd(), receipt.status().name());
    }

    private static CheckoutState checkoutSnapshot(CheckoutService service) {
        return new CheckoutState(service.repository().listSorted().stream()
                .map(InterfaceExperimentsService::receiptState).toList(), service.gatewayCalls());
    }
}
