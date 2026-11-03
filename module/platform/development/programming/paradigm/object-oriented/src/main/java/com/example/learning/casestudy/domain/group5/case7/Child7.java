package com.example.learning.casestudy.domain.group5.case7;

import com.example.learning.casestudy.utils.group5.case7.Child7Utils;

public final class Child7 extends Base7 {
    public static String kind() {
        return Child7Utils.kind();
    }

    @Override public String render() {
        return "child-instance";
    }
}
