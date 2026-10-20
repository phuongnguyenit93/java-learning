package com.example.learning.oop.interface_chapter.model;

public interface DocumentPublisher {
    void publish(String id);

    default String roleName() { return "publish"; }
}
