package com.example.learning.casestudy.adapter.group4.case10;

import com.example.learning.casestudy.dto.group4.case10.ItemView;
import com.example.learning.casestudy.model.group4.case10.MutableItem;
import com.example.learning.casestudy.port.group4.case10.ItemReader;
import java.util.ArrayList;
import java.util.List;

public final class A10 implements ItemReader {
    private final List<MutableItem> source;
    public A10(List<MutableItem> source){this.source=source;}
    public List<ItemView> getItems(){
        List<ItemView> values=new ArrayList<>();
        for(MutableItem x:source)
            values.add(new ItemView(x.sku,x.count));
        return List.copyOf(values);
    }
}
