package com.example.learning.casestudy.adapter.group4.case10;

import com.example.learning.casestudy.dto.group4.case10.ItemView;
import com.example.learning.casestudy.model.group4.case10.MutableItem;
import com.example.learning.casestudy.port.group4.case10.ItemReader;
import java.util.ArrayList;
import java.util.List;

public final class B10 implements ItemReader {
    private final List<ItemView> values;
    public B10(List<MutableItem> source){
        List<ItemView> copy=new ArrayList<>();
        for(MutableItem x:source)
            copy.add(new ItemView(x.sku,x.count));
        values=List.copyOf(copy);
    }
    public List<ItemView> getItems(){return values;}
}
