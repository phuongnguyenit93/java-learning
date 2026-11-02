package com.example.learning.casestudy.adapter.group4.case7;

import com.example.learning.casestudy.port.group4.case7.FormatPort;

public final class FormatterImpl implements FormatPort {
    public String format(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("value");
        return "[" + value.trim() + "]";
    }
}
