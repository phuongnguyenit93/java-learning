package com.example.learning.oop.abstraction.service;

import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.BookInput;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.BookView;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.CatalogSearchRequest;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.CatalogSearchResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public final class BookCatalogService {
    public CatalogSearchResponse search(CatalogSearchRequest request) {
        if (request == null || request.books() == null || request.books().size() > 50) {
            throw new IllegalArgumentException("books must contain at most 50 entries");
        }
        if (request.keyword() == null || request.keyword().isBlank()
                || request.keyword().length() > 100 || request.limit() < 1
                || request.limit() > 50) {
            throw new IllegalArgumentException("keyword and limit (1..50) are required");
        }
        if (request.books().stream().anyMatch(book -> book == null)) {
            throw new IllegalArgumentException("books must not contain null");
        }
        Catalog catalog = new InMemoryCatalog(request.books());
        String keyword = request.keyword().trim().toLowerCase(Locale.ROOT);
        List<BookView> allMatches = catalog.searchByTitle(keyword);
        List<BookView> limited = allMatches.subList(0, Math.min(request.limit(), allMatches.size()));
        String isbn = request.lookupIsbn();
        BookView lookup = isbn == null ? null : catalog.findByIsbn(isbn.trim()).orElse(null);
        return new CatalogSearchResponse(limited, lookup, lookup != null,
                allMatches.size(), true);
    }

    private interface Catalog {
        Optional<BookView> findByIsbn(String isbn);

        List<BookView> searchByTitle(String keyword);
    }

    /**
     * Map is an entirely private implementation choice. Clients observe stable
     * domain results sorted by ISBN, never a live Map, cursor or storage exception.
     */
    private static final class InMemoryCatalog implements Catalog {
        private final Map<String, BookView> byIsbn = new HashMap<>();

        private InMemoryCatalog(List<BookInput> books) {
            for (BookInput input : List.copyOf(books)) {
                if (input.isbn() == null || input.isbn().isBlank()
                        || input.title() == null || input.title().isBlank()) {
                    throw new IllegalArgumentException("Book requires ISBN and title");
                }
                BookView book = new BookView(input.isbn().trim(), input.title().trim());
                if (byIsbn.putIfAbsent(book.isbn(), book) != null) {
                    throw new IllegalArgumentException("Duplicate ISBN: " + book.isbn());
                }
            }
        }

        @Override
        public Optional<BookView> findByIsbn(String isbn) {
            if (isbn.isBlank()) {
                throw new IllegalArgumentException("lookupIsbn must not be blank");
            }
            return Optional.ofNullable(byIsbn.get(isbn));
        }

        @Override
        public List<BookView> searchByTitle(String keyword) {
            List<BookView> found = new ArrayList<>();
            byIsbn.values().stream()
                    .filter(book -> book.title().toLowerCase(Locale.ROOT).contains(keyword))
                    .sorted(Comparator.comparing(BookView::isbn))
                    .forEach(found::add);
            return List.copyOf(found);
        }
    }
}
