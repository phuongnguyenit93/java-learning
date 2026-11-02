package com.example.learning.casestudy.model.group3.case10;

import com.example.learning.casestudy.model.group3.case10.Item10;
import java.util.List;

public class Bad10 {
    public final List<Item10> items;
    public Bad10(List<Item10> data) {
        items = List.copyOf(data);
    }
}
