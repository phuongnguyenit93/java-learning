package com.example.learning.casestudy.utils.group11.case10;

import com.example.learning.casestudy.domain.group11.case10.Animal10;
import java.util.List;

public final class Demo10Utils {
    private Demo10Utils() {
    }

    public static String sounds(List<? extends Animal10> source) {
        StringBuilder result = new StringBuilder();
        for (Animal10 a : source) result.append(a.sound()).append(";");
        return result.toString();
    }
}
