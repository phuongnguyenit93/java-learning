package com.example.learning.casestudy.utils.group2.case14;

import com.example.learning.casestudy.domain.group2.case14.Payment14;
import com.example.learning.casestudy.dto.group2.case14.PaymentDto;

public final class Case14Utils {
    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    public static void reject(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected invalid input to be rejected");
    }
    public static Payment14 fromDto(PaymentDto dto) {
        if (dto == null) throw new IllegalArgumentException("Missing body");
        return new Payment14(dto.reference(), dto.amountMinor());
    }
}
