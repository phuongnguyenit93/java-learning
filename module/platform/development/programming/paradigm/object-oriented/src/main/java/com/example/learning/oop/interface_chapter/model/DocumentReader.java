package com.example.learning.oop.interface_chapter.model;

import java.util.List;
import java.util.Optional;

public interface DocumentReader {
    Optional<DocumentEntry> find(String id);
    List<DocumentEntry> list();

    default String roleName() { return "read"; }
}
