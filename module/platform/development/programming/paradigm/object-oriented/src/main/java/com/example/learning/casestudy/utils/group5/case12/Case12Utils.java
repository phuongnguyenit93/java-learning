package com.example.learning.casestudy.utils.group5.case12;

import com.example.learning.casestudy.port.group5.case12.Renderer;

public final class Case12Utils {
    public static void contract(Renderer renderer) {
        assert renderer.render("") != null;
        assert renderer.render("text") != null;
    }
}
