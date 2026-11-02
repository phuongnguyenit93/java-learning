package com.example.learning.casestudy.adapter.group5.case12;

import com.example.learning.casestudy.port.group5.case12.Renderer;
import java.util.Locale;

public record UpperRenderer(Renderer delegate) implements Renderer {
    public String render(String content) {
        return delegate.render(content).toUpperCase(Locale.ROOT);
    }
}
