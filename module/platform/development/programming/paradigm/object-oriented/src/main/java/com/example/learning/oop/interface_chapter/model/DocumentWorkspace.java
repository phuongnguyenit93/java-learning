package com.example.learning.oop.interface_chapter.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One object with independent reader/writer/publisher interfaces and one guarded state. */
public final class DocumentWorkspace implements DocumentReader, DocumentWriter, DocumentPublisher {
    private static final int MAX_DOCUMENTS = 15;
    private final Map<String, DocumentEntry> entries = new HashMap<>();

    @Override public Optional<DocumentEntry> find(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        return Optional.ofNullable(entries.get(id));
    }

    @Override public List<DocumentEntry> list() {
        return entries.values().stream()
                .sorted(Comparator.comparing(DocumentEntry::id)).toList();
    }

    @Override public void save(String id, String title, String content) {
        DocumentEntry candidate = new DocumentEntry(id, title, content, DocumentEntry.Status.DRAFT);
        DocumentEntry previous = entries.get(id);
        if (previous != null && previous.status() == DocumentEntry.Status.PUBLISHED) {
            throw new IllegalStateException("published document cannot be edited");
        }
        if (previous == null && entries.size() == MAX_DOCUMENTS) {
            throw new IllegalStateException("document capacity exceeded");
        }
        entries.put(id, candidate);
    }

    @Override public void publish(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        DocumentEntry current = entries.get(id);
        if (current == null) throw new IllegalStateException("document does not exist");
        if (current.status() == DocumentEntry.Status.PUBLISHED) {
            throw new IllegalStateException("document already published");
        }
        entries.put(id, new DocumentEntry(current.id(), current.title(), current.content(),
                DocumentEntry.Status.PUBLISHED));
    }

    @Override public String roleName() {
        // Two independent interface defaults conflict: the class must resolve explicitly.
        return DocumentReader.super.roleName() + "+" + DocumentPublisher.super.roleName();
    }
}
