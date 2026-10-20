package com.example.learning.oop.abstraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.BookInput;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.CatalogSearchRequest;
import com.example.learning.oop.abstraction.service.BookCatalogService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookCatalogServiceTest {
    private final BookCatalogService service = new BookCatalogService();
    private final List<BookInput> books = List.of(
            new BookInput("S-02", "Spring Data"),
            new BookInput("J-03", "Advanced Java"),
            new BookInput("J-01", "Java OOP"));

    @Test
    void stableCaseInsensitiveSearchAndLookupHideStorageDetails() {
        var result = service.search(new CatalogSearchRequest(books, " JAVA ", 2, "S-02"));
        assertEquals(2, result.totalMatches());
        assertEquals(List.of("J-01", "J-03"), result.matches().stream().map(x -> x.isbn()).toList());
        assertTrue(result.found());
        assertEquals("Spring Data", result.lookup().title());
        assertTrue(result.simulatedOnly());
        assertThrows(UnsupportedOperationException.class, result.matches()::clear);
    }

    @Test
    void limitAndMissingLookupHaveDeterministicMeaning() {
        var limited = service.search(new CatalogSearchRequest(books, "java", 1, "MISSING"));
        assertEquals(2, limited.totalMatches());
        assertEquals(1, limited.matches().size());
        assertEquals("J-01", limited.matches().get(0).isbn());
        assertFalse(limited.found());
        assertEquals(null, limited.lookup());
    }

    @Test
    void dataIsRequestLocalAndDoesNotExposeTheCallerList() {
        List<BookInput> input = new ArrayList<>(books);
        var before = service.search(new CatalogSearchRequest(input, "java", 10, null));
        input.clear();
        var after = service.search(new CatalogSearchRequest(input, "java", 10, null));
        assertEquals(2, before.matches().size());
        assertEquals(0, after.matches().size());
        assertThrows(UnsupportedOperationException.class, before.matches()::clear);
    }

    @Test
    void malformedCatalogAndDuplicatesFailAtTheBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> service.search(new CatalogSearchRequest(books, "", 2, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.search(new CatalogSearchRequest(books, "java", 0, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.search(new CatalogSearchRequest(
                        List.of(new BookInput("X", "One"), new BookInput("X", "Two")),
                        "java", 2, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.search(new CatalogSearchRequest(
                        List.of(new BookInput("X", " ")), "java", 2, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.search(new CatalogSearchRequest(books, "java", 2, " ")));
        List<BookInput> malformed = new ArrayList<>();
        malformed.add(new BookInput("J-01", "Java"));
        malformed.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.search(new CatalogSearchRequest(malformed, "java", 2, null)));
    }
}
