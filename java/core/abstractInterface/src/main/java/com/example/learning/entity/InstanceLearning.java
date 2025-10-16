package com.example.learning.entity;

import com.example.learning.abstractClass.AbstractA;
import com.example.learning.interfaced.InterfaceB;
import com.example.learning.interfaced.InterfaceA;

// 1 class can only extend 1 abstract class but can implement many interface
public class InstanceLearning extends AbstractA implements InterfaceB, InterfaceA {
    public InstanceLearning (String name) {
        super(name);
    }

    public void absA1() {
        System.out.println(getAbsA1());
    }

    // abstract method must be override
    @Override
    public void absA2() {
        System.out.println("Status absA2 - Abstract");
    }

    // interface method must be override if they are not default or static
    @Override
    public void intfA1() {
        System.out.println("INTF A1");
    }

    public void intfA3() {
        InterfaceA.intfA3();
    }

    @Override
    public void intfB1() {
        System.out.println("INTF B1");
    }

    public void intfB3() {
        InterfaceB.intfB3();
    }
}
