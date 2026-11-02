package com.example.learning.casestudy.application.group9.case6;

import com.example.learning.casestudy.adapter.group9.case6.PaymentGateway;
import com.example.learning.casestudy.adapter.group9.case6.ReceiptSender;
import com.example.learning.casestudy.domain.group9.case6.Receipt;
import com.example.learning.casestudy.domain.group9.case6.Result;
import com.example.learning.casestudy.domain.group9.case6.Status;

public final class CheckoutFacade {
    public final PaymentGateway payment;
    public final ReceiptSender sender;
    public CheckoutFacade(PaymentGateway p, ReceiptSender s) { payment = p; sender = s; }
    public Result checkout(String requestId, int cents) {
        Receipt receipt = payment.charge(requestId, cents);
        try {
            sender.send(receipt);
            return new Result(Status.COMPLETED, receipt);
        } catch (IllegalStateException deliveryError) {
            return new Result(Status.PAID_RECEIPT_PENDING, receipt);
        }
    }
}
