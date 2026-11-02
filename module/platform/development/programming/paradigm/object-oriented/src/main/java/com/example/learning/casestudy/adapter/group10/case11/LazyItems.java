package com.example.learning.casestudy.adapter.group10.case11;


import com.example.learning.casestudy.adapter.group10.case11.Session;

import java.util.List;


public class LazyItems {

    final Session session;

    public LazyItems(Session s){
        session=s;
    }

    public List<String> get(){

        if(!session.open)throw new IllegalStateException("lazy session closed");

        return List.of("A","B");

    }

}
