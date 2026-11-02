package com.example.learning.casestudy.model.group3.case10;

import com.example.learning.casestudy.dto.group3.case10.ItemView;
import com.example.learning.casestudy.model.group3.case10.Item10;
import java.util.ArrayList;
import java.util.List;

public class Safe10 {
    public final List<ItemView> items;
    public Safe10(List<Item10> input) {
        List<ItemView> snapshot = new ArrayList<>();
        for (Item10 x : input)snapshot.add(new ItemView(x.sku, x.count));
        items = List.copyOf(snapshot);
    }
}
