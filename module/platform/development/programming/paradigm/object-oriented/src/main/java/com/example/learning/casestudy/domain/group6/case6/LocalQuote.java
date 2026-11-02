package com.example.learning.casestudy.domain.group6.case6;

import com.example.learning.casestudy.port.group6.case6.Quote;
import com.example.learning.casestudy.utils.group6.case6.Case6Utils;
import java.math.BigDecimal;

public class LocalQuote implements Quote {
    public BigDecimal total(BigDecimal a, String c) { return Case6Utils.normalize(a, c); }
}
