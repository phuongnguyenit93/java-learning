package com.example.learning.casestudy.domain.group4.case4;

import com.example.learning.casestudy.port.group4.case4.AuditLabel;
import com.example.learning.casestudy.port.group4.case4.PaymentLabel;

// class Invalid implements AuditLabel, PaymentLabel {}
// Bỏ comment ở trên -> Java báo conflicting defaults.
public final class Combined implements AuditLabel, PaymentLabel {
    @Override public String label() {
        return PaymentLabel.super.label() + " / "
             + AuditLabel.super.label();
    }
}
