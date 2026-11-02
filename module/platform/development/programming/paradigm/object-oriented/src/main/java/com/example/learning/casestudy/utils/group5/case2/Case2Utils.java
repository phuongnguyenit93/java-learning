package com.example.learning.casestudy.utils.group5.case2;

import com.example.learning.casestudy.domain.group5.case2.Account;

public final class Case2Utils {
    public static void contract(Account account) {
        long before = account.balance();
        try {
            account.withdraw(before + 1);
            throw new AssertionError("must reject overdraft");
        } catch (IllegalArgumentException expected) {
        }
        assert account.balance() == before;
        if (before > 0) {
            account.withdraw(before);
            assert account.balance() == 0;
        }
    }
}
