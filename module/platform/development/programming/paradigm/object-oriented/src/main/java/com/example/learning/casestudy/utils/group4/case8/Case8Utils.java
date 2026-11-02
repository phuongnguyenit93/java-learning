package com.example.learning.casestudy.utils.group4.case8;

import com.example.learning.casestudy.dto.group4.case8.TransferCommand;
import java.util.Map;

public final class Case8Utils {
    private Case8Utils() {}

    public static long brokenExecute(Map<String,Object> input) {
        return (Long) input.get("amount"); // Nhầm String / thiếu key.
    }

    public static long execute(TransferCommand command) {
        return command.amount();
    }

    public static void check(boolean b) { if (!b) throw new AssertionError(); }
}
