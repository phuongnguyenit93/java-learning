package com.example.learning.casestudy.domain.group3.case14;

import com.example.learning.casestudy.domain.group3.case14.Line;
import com.example.learning.casestudy.domain.group3.case14.Quote;

public class PricePolicy {
    public Quote quote(Line l) {
        if (l.unitPrice()<0 || l.qty()<1 || l.discount()<0) throw new IllegalArgumentException();
        long subtotal = Math.multiplyExact(l.unitPrice(), l.qty());
        if (l.discount()>subtotal)throw new IllegalArgumentException();
        return new Quote(subtotal, subtotal-l.discount());
    }
}
