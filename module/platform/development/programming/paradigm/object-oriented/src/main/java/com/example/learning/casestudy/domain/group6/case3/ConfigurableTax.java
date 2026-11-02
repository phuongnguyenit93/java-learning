package com.example.learning.casestudy.domain.group6.case3;

import com.example.learning.casestudy.port.group6.case3.Tax;
import com.example.learning.casestudy.utils.group6.case3.Case3Utils;
import java.math.BigDecimal;

public class ConfigurableTax implements Tax {
    private final BigDecimal rate;
    public ConfigurableTax(BigDecimal rate) { this.rate = rate; }
    public BigDecimal tax(BigDecimal amount) { return Case3Utils.rounded(amount, rate); }
}
