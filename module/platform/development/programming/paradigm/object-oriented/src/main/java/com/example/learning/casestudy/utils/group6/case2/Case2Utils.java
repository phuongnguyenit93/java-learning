package com.example.learning.casestudy.utils.group6.case2;

import com.example.learning.casestudy.domain.group6.case2.Kind;
import com.example.learning.casestudy.domain.group6.case2.Transaction;
import com.example.learning.casestudy.dto.group6.case2.TxView;
import com.example.learning.casestudy.port.group6.case2.TxPolicy;
import java.util.Map;

public final class Case2Utils {
    private Case2Utils() {}

    static final Map<Kind, TxPolicy> POLICIES = Map.of(
    Kind.CARD, t -> new TxView("card", t.cents() + 50),
    Kind.CASH, t -> new TxView("cash", t.cents()));

    public static TxView resolve(Transaction t) {
        TxPolicy p = POLICIES.get(t.kind());
        if (p == null) throw new IllegalArgumentException("Unsupported transaction");
        return p.apply(t);
    }

    public static TxView controller(Transaction t) { return resolve(t); }

    public static TxView service(Transaction t) { return resolve(t); }

    public static TxView ui(Transaction t) { return resolve(t); }
}
