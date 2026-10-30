package com.example.learning.casestudy.temp.g1.c8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.learning.casestudy.temp.g1.c8.Case8Service.Case8Request;
import com.example.learning.casestudy.temp.g1.c8.Case8Service.EmployeeInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class Case8ServiceTest {
    private final Case8Service service = new Case8Service();
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 1, 1);

    @Test
    void identityComesFromEmployeeIdRatherThanNameAndBirthDate() {
        EmployeeInput a = new EmployeeInput("E-1", "An", BIRTH_DATE);
        EmployeeInput b = new EmployeeInput("E-2", "An", BIRTH_DATE);
        EmployeeInput sameIdentity = new EmployeeInput("E-1", "An", BIRTH_DATE);

        var result = service.execute(new Case8Request(
                List.of(a, b, sameIdentity),
                new EmployeeInput("E-2", "Another name", LocalDate.of(2000, 2, 2))));

        assertEquals(2, result.uniqueEmployeeCount());
        assertEquals(List.of("E-1", "E-2"), result.uniqueEmployeeIds());
        assertTrue(result.containsProbe());
        assertFalse(service.execute(new Case8Request(List.of(a, b),
                new EmployeeInput("E-3", "An", BIRTH_DATE))).containsProbe());
    }

    @Test
    void equalityIsSymmetricTransitiveAndHashConsistent() {
        var a = new Case8Service.Employee("E-1", "An", BIRTH_DATE);
        var b = new Case8Service.Employee("E-1", "Binh", LocalDate.of(2000, 2, 2));
        var c = new Case8Service.Employee("E-1", "Chi", LocalDate.of(2001, 3, 3));
        var different = new Case8Service.Employee("E-2", "An", BIRTH_DATE);

        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(b, c);
        assertEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, different);
        assertEquals(2, Set.of(a, different).size());
    }

    @Test
    void invalidEmployeesAreRejected() {
        EmployeeInput valid = new EmployeeInput("E-1", "An", BIRTH_DATE);
        assertThrows(IllegalArgumentException.class, () -> service.execute(null));
        assertThrows(IllegalArgumentException.class, () -> service.execute(new Case8Request(null, valid)));
        assertThrows(IllegalArgumentException.class, () -> service.execute(new Case8Request(List.of(valid), null)));
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new Case8Request(List.of(new EmployeeInput("", "An", BIRTH_DATE)), valid)));
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new Case8Request(List.of(new EmployeeInput(null, "An", BIRTH_DATE)), valid)));
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new Case8Request(List.of(new EmployeeInput("E-1", null, BIRTH_DATE)), valid)));
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new Case8Request(List.of(new EmployeeInput("E-1", "An", null)), valid)));
        assertThrows(IllegalArgumentException.class, () -> service.execute(
                new Case8Request(List.of(valid), new EmployeeInput(" ", "An", BIRTH_DATE))));
    }

    @Test
    void httpEndpointExecutesIdentityScenarioAndRejectsBadIds() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Case8Controller(service)).build();
        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/8/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "employees": [
                            {"employeeId":"E-1","fullName":"An","birthDate":"1995-01-01"},
                            {"employeeId":"E-2","fullName":"An","birthDate":"1995-01-01"},
                            {"employeeId":"E-1","fullName":"An","birthDate":"1995-01-01"}
                          ],
                          "probe":{"employeeId":"E-2","fullName":"An","birthDate":"1995-01-01"}
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uniqueEmployeeCount").value(2))
                .andExpect(jsonPath("$.containsProbe").value(true))
                .andExpect(jsonPath("$.uniqueEmployeeIds[0]").value("E-1"))
                .andExpect(jsonPath("$.uniqueEmployeeIds[1]").value("E-2"));

        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/8/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "employees": [{"employeeId":"","fullName":"An","birthDate":"1995-01-01"}],
                          "probe":{"employeeId":"E-2","fullName":"An","birthDate":"1995-01-01"}
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMPLOYEE"));
    }
}
