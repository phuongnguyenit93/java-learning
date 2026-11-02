package com.example.learning.casestudy.utils.group10.case11;


import com.example.learning.casestudy.domain.group10.case11.OrderEntity;

import com.example.learning.casestudy.dto.group10.case11.OrderDto;

import java.util.List;


public final class Case11Utils {

    private Case11Utils() {

    }


    public static OrderDto mapWithinBoundary(OrderEntity entity){

        return new OrderDto(entity.id,List.copyOf(entity.items.get()));

    }


    public static void check(boolean ok){
        if(!ok)throw new AssertionError();
    }

}
