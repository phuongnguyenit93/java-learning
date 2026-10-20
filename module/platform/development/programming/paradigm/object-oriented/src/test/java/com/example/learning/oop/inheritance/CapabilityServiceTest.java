package com.example.learning.oop.inheritance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.CapabilitiesRequest;
import com.example.learning.oop.inheritance.service.CapabilityService;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityServiceTest {
    private final CapabilityService service = new CapabilityService();

    @Test
    void explicitlyResolvesTwoUnrelatedDefaultsAndClassMethodWins() {
        var output = service.resolve(new CapabilitiesRequest(" DOC-1 ", " Java, OOP,Java17 "));
        assertEquals("PRINT+SEARCH:DOC-1", output.explicitConflictResolution());
        assertEquals("CLASS_WINS", output.classWinsResolution());
        assertEquals(List.of("java", "oop", "java17"), output.normalizedTags());
        assertTrue(output.simulatedOnly());
        assertThrows(UnsupportedOperationException.class, () -> output.normalizedTags().clear());
    }

    @Test
    void invalidTagOrItemIsNotSilentlyIgnored() {
        assertThrows(IllegalArgumentException.class, () -> service.resolve(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(new CapabilitiesRequest("", "one")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(new CapabilitiesRequest("DOC", null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(new CapabilitiesRequest("DOC", "a,,b")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(new CapabilitiesRequest("DOC", "a,")));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(new CapabilitiesRequest("DOC", "x".repeat(201))));
    }
}
