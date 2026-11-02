package com.example.learning.casestudy.application.group5.case12;

import com.example.learning.casestudy.port.group5.case12.Renderer;

public record Screen(Renderer renderer) {
    public String show(String message) {
        return renderer.render(message);
    }
}
