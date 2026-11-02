package com.example.learning.casestudy.utils.group11.case11;

import com.example.learning.casestudy.domain.group11.case11.Animal11;
import com.example.learning.casestudy.domain.group11.case11.Dog11;
import java.util.List;

public final class Demo11Utils {
    private Demo11Utils() {
    }

    public static void copyAnimals(List<? extends Animal11> source,
    List<? super Animal11> destination) {
        for (Animal11 item : source) destination.add(item);
        // source.add(new Dog11("bad")); // lỗi compile: không thể add vào extends
    }
}
