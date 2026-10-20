package com.example.learning.oop.subtyping;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.learning.oop.subtyping.controller.SubtypingExperimentController;
import com.example.learning.oop.subtyping.exception.SubtypingExperimentExceptionHandler;
import com.example.learning.oop.subtyping.service.SubtypingExperimentService;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class SubtypingHttpTest {
    private MockMvc http;

    @BeforeEach
    void prepare() {
        http = MockMvcBuilders.standaloneSetup(
                new SubtypingExperimentController(new SubtypingExperimentService()))
                .setControllerAdvice(new SubtypingExperimentExceptionHandler()).build();
    }

    @Test
    void routesRemainScopedAndUnique() {
        RequestMapping mapping = SubtypingExperimentController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertEquals("/oop/subtyping", mapping.value()[0]);
        Set<String> methods = Arrays.stream(SubtypingExperimentController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("/dispatch", "/discount-contract", "/geometry-contract"), methods);
    }

    @Test
    void dynamicDispatchAndAdapterProduceObservableResponses() throws Exception {
        http.perform(post("/oop/subtyping/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"implementation":"CARD","amountCents":250}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeType").value("CardPayment"))
                .andExpect(jsonPath("$.receipt").value("CARD:APPROVED:250"))
                .andExpect(jsonPath("$.overloadForBaseReference").value("OVERLOAD:PaymentMethod"))
                .andExpect(jsonPath("$.overloadForConcreteReference").value("OVERLOAD:CardPayment"));
        http.perform(post("/oop/subtyping/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"implementation":"LEGACY_ADAPTER","amountCents":200}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legacyUsesAdapter").value(true))
                .andExpect(jsonPath("$.receipt").value("LEGACY:APPROVED:200"));
    }

    @Test
    void violationIsReportedAsContractResultNotServerFailure() throws Exception {
        http.perform(post("/oop/subtyping/discount-contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"implementation":"STRICT_MINIMUM",
                                 "pricesCents":[5,50],"minimumCents":50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allCasesPass").value(false))
                .andExpect(jsonPath("$.cases[0].satisfiedContract").value(false))
                .andExpect(jsonPath("$.cases[1].netCents").value(50));
    }

    @Test
    void squareIsActuallyWrongBehavioralSubtype() throws Exception {
        http.perform(post("/oop/subtyping/geometry-contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"SQUARE_AS_RECTANGLE","initialWidth":2,
                                 "initialHeight":2,"newWidth":5,"newHeight":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rectangleContractRespected").value(false))
                .andExpect(jsonPath("$.finalArea").value(9))
                .andExpect(jsonPath("$.expectedRectangleArea").value(15))
                .andExpect(jsonPath("$.immutableRectangleArea").value(15));
    }

    @Test
    void invalidDomainAndMalformedJsonReceiveScoped400() throws Exception {
        http.perform(post("/oop/subtyping/geometry-contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"SQUARE_AS_RECTANGLE","initialWidth":2,
                                 "initialHeight":4,"newWidth":5,"newHeight":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        http.perform(post("/oop/subtyping/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }
}
