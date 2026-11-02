package com.example.learning.casestudy.utils.group1.case7;

import com.example.learning.casestudy.domain.group1.case7.Payment;
import com.example.learning.casestudy.domain.group1.case7.Status7;
import com.example.learning.casestudy.dto.group1.case7.PaymentResponse;
import com.example.learning.casestudy.dto.group1.case7.PaymentRow;
import com.example.learning.casestudy.dto.group1.case7.PaymentView;

public final class Case7Utils {
    private Case7Utils() {}

    public static PaymentRow toRow(Payment p) {
        return new PaymentRow(p.id(), p.status().name());
    }

    public static PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.id(), p.status() == Status7.PAID);
    }

    public static PaymentView toView(PaymentResponse dto) {
        return new PaymentView(dto.completed() ? "Đã thanh toán" : "Đang chờ");
    }

    public static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
