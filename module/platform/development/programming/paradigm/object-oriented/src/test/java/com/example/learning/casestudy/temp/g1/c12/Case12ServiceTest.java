package com.example.learning.casestudy.temp.g1.c12;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Case12ServiceTest {

    private final Case12Service service = new Case12Service();

    @Test
    void quotesCommasDoubleQuotesAndNewlinesExactlyAsOriginalCase() {
        Case12Service.ExportResult result = service.export(List.of(
                new Case12Service.Row("An", 100),
                new Case12Service.Row("B, \"VIP\"", 200),
                new Case12Service.Row("Dòng 1\nDòng 2", 300)));

        String expected = "customer,amount\r\n"
                + "\"An\",100\r\n"
                + "\"B, \"\"VIP\"\"\",200\r\n"
                + "\"Dòng 1\nDòng 2\",300\r\n";
        assertEquals(expected, result.csv());
        assertEquals(3, result.rowCount());
    }

    @Test
    void emptyRowsReturnHeaderOnlyAndPreserveAmountSemantics() {
        Case12Service.ExportResult empty = service.export(List.of());
        assertEquals("customer,amount\r\n", empty.csv());
        assertEquals(0, empty.rowCount());

        Case12Service.ExportResult amount = service.export(List.of(
                new Case12Service.Row("Zero", 0),
                new Case12Service.Row("Negative", -4)));
        assertEquals("customer,amount\r\n\"Zero\",0\r\n\"Negative\",-4\r\n",
                amount.csv());
    }

    @Test
    void rejectsNullCustomerRowsAndRowElements() {
        assertEquals("customer", assertThrows(IllegalArgumentException.class,
                () -> service.export(Collections.singletonList(
                        new Case12Service.Row(null, 1)))).getMessage());
        assertEquals("rows", assertThrows(IllegalArgumentException.class,
                () -> service.export(null)).getMessage());
        assertEquals("row", assertThrows(IllegalArgumentException.class,
                () -> service.export(Collections.singletonList(null))).getMessage());
    }

    @Test
    void httpBoundarySerializesActualCsvAndRejectsInvalidCustomer() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Case12Controller(service)).build();

        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/12/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{\"customer\":\"B, \\\"VIP\\\"\",\"amount\":200}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csv").value("customer,amount\r\n\"B, \"\"VIP\"\"\",200\r\n"))
                .andExpect(jsonPath("$.rowCount").value(1));

        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/12/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{\"customer\":null,\"amount\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("customer"));

        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/12/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("rows"));
    }
}
