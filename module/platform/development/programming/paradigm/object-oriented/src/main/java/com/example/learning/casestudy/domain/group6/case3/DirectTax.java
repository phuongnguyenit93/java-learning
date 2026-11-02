package com.example.learning.casestudy.domain.group6.case3;

import com.example.learning.casestudy.port.group6.case3.Tax;
import com.example.learning.casestudy.utils.group6.case3.Case3Utils;
import java.math.BigDecimal;

public class DirectTax implements Tax {
    public BigDecimal tax(BigDecimal amount) {
        return Case3Utils.rounded(amount, new BigDecimal("0.10"));
    }
}
