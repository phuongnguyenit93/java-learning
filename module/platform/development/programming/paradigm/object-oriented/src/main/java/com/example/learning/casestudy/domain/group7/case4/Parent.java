package com.example.learning.casestudy.domain.group7.case4;

import java.util.ArrayList;
import java.util.List;

public class Parent {
    public final String id;
    public final List<Child> children = new ArrayList<>();
    public Parent(String id){
        this.id = id;

    }
}
