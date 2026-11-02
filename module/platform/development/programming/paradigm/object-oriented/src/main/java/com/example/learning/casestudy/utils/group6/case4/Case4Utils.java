package com.example.learning.casestudy.utils.group6.case4;

import com.example.learning.casestudy.port.group6.case4.Exportable;
import java.util.List;

public final class Case4Utils {
    private Case4Utils() {}

    public static String exportAll(List<? extends Exportable> records) {
        return String.join("|", records.stream().map(Exportable::export).toList());
    }
}
