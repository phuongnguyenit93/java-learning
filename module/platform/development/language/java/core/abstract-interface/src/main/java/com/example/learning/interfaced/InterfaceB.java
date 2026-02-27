package com.example.learning.interfaced;

public interface InterfaceB {
    // Interface can't have constructor or field unlike abstract
    // 1 class can implement many interface
    // Use for share logic code for multi class

    void intfB1();

    default void intfB2() {
        // Available from Java 8
        System.out.println("This is intfB2 - default instance INTF");
    }

    static void intfB3 () {
        //Available from Java 9
        System.out.println("This is intfB3 - static instance INTF");
    }
}
