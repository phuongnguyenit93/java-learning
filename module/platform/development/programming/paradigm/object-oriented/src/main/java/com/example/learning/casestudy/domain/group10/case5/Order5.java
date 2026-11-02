package com.example.learning.casestudy.domain.group10.case5;


import com.example.learning.casestudy.model.group10.case5.ItemView;

import java.util.ArrayList;

import java.util.List;


public class Order5 {

    private final List<ItemView> items=new ArrayList<>();

    public void add(String sku,int qty,long price){

        if(sku==null||sku.isBlank()||qty<=0||price<0)
            throw new IllegalArgumentException();

        items.add(new ItemView(sku,qty,price));

    }

    public List<ItemView> items(){
        return List.copyOf(items);
    }

    public long total(){

        long sum=0;

        for(ItemView i:items)
            sum=Math.addExact(sum,Math.multiplyExact(i.qty(),i.unitPrice()));

        return sum;

    }

}
