package com.example.learning.casestudy.domain.group10.case11;


import com.example.learning.casestudy.adapter.group10.case11.LazyItems;


public class OrderEntity {

    public final String id;
    public final LazyItems items;

    public OrderEntity(String id,LazyItems items){
        this.id=id;
        this.items=items;
    }

}
