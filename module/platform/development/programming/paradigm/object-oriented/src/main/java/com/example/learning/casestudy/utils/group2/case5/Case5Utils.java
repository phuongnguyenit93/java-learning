package com.example.learning.casestudy.utils.group2.case5;

import com.example.learning.casestudy.domain.group2.case5.AppConfig;
import com.example.learning.casestudy.model.group2.case5.UnsafeConfig;
import java.util.Map;

public final class Case5Utils {
    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    public static String url() {
        return UnsafeConfig.URL;
    }
    public static AppConfig load(Map<String,String> environment) {
        return new AppConfig(environment.get("APP_URL"));
    }
}
