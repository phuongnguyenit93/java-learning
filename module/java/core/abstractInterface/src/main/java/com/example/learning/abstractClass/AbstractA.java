package com.example.learning.abstractClass;

import lombok.Getter;

@Getter
public abstract class AbstractA {
    // Abstract class can have field;
    String absA1;

    public abstract void absA2();

    // Abstract class can have constructor - But can't create instance itself - Only create from class that extended it
    public AbstractA(String status1) {
        this.absA1 = status1;
        System.out.println("Abstract A has been created with name : " + status1);
    }

    public void absA3 () {
        System.out.println("This is absA3 of abstract class");
    }
}
