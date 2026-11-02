package com.example.learning.casestudy.utils.group2.case9;

import com.example.learning.casestudy.application.group2.case9.BadFactory;
import com.example.learning.casestudy.domain.group2.case9.Settings;
import com.example.learning.casestudy.model.group2.case9.MutableSettings;

public final class Case9Utils {
    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    public static MutableSettings settings() {
        return BadFactory.SINGLETON;
    }
    public static Settings forUser(String requestedTheme) {
        return new Settings(requestedTheme);
    }
}
