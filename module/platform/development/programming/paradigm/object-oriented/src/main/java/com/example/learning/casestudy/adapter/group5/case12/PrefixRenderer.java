package com.example.learning.casestudy.adapter.group5.case12;

import com.example.learning.casestudy.port.group5.case12.Renderer;

public record PrefixRenderer(Renderer delegate, String prefix) implements Renderer {
    public String render(String content) {
        return prefix + delegate.render(content);
    }
}
