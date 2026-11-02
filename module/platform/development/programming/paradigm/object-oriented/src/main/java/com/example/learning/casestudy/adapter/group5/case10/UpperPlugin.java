package com.example.learning.casestudy.adapter.group5.case10;

import com.example.learning.casestudy.port.group5.case10.TextStrategy;
import java.util.Locale;

public final class UpperPlugin implements TextStrategy {
    public String transform(String input) {
        return input.toUpperCase(Locale.ROOT);
    }
}
