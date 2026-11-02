package com.example.learning.casestudy.adapter.group5.case10;

import com.example.learning.casestudy.port.group5.case10.TextStrategy;

public final class PrefixPlugin implements TextStrategy {
    public String transform(String input) {
        return "PLUGIN:" + input;
    }
}
