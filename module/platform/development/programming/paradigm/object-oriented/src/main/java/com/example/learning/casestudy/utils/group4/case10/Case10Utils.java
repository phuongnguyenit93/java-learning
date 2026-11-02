package com.example.learning.casestudy.utils.group4.case10;

import com.example.learning.casestudy.dto.group4.case10.ItemView;
import com.example.learning.casestudy.model.group4.case10.MutableItem;
import com.example.learning.casestudy.port.group4.case10.ItemReader;
import java.util.List;

public final class Case10Utils {
    private Case10Utils() {}

    public static void check(boolean b) {
        if (!b) {
            throw new AssertionError();
        }
    }

    public static void contract(ItemReader reader, MutableItem source) {
        List<ItemView> before = reader.getItems();
        check(before.get(0).count() == 2);
        try {
            before.clear();
            throw new AssertionError("mutable list");
        } catch (UnsupportedOperationException expected) {
        }
        source.count = 9;
        check(before.get(0).count() == 2); // Snapshot cũ độc lập.
    }
}
