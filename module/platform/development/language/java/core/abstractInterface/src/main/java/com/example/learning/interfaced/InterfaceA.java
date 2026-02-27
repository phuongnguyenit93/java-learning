package com.example.learning.interfaced;

public interface InterfaceA {
    // Interface can't have constructor or field unlike abstract
    // 1 class can implement many interface
    // Use for share logic code for multi class

    void intfA1();

    default void intfA2() {
        // Available from Java 8 - Abstract with body
        System.out.println("This is interface A2 - default INTF");
    }

    static void intfA3 () {
        //Available from Java 9 - Static abstract with body
        System.out.println("This is status A3 - static INTF");
    }
}
