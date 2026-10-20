package com.example.learning.oop.design_patterns;

import com.example.learning.oop.design_patterns.controller.DesignPatternExperimentController;
import com.example.learning.oop.design_patterns.exception.DesignPatternExceptionHandler;
import com.example.learning.oop.design_patterns.service.DesignPatternExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DesignPatternsHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                new DesignPatternExperimentController(new DesignPatternExperimentService()))
                .setControllerAdvice(new DesignPatternExceptionHandler()).build();
    }

    @Test
    void pricingJsonCalculatesActualStrategyAndDecoratorOrder() throws Exception {
        mvc().perform(post("/oop/design-patterns/pricing/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"subtotalVnd":100000,"deliveryFeeVnd":20000,
                     "strategy":"MEMBER_5","order":"DISCOUNT_THEN_FEE"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected.payableVnd").value(115000))
                .andExpect(jsonPath("$.allOrders[1].payableVnd").value(114000))
                .andExpect(jsonPath("$.selected.executionTrace[1]").value("DISCOUNT_MEMBER_5:5000"));
    }

    @Test
    void tasksJsonRecordsObserverEventsAndActualUndoRedo() throws Exception {
        mvc().perform(post("/oop/design-patterns/tasks/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actions":[
                      {"type":"ADD","id":"T-1","title":"Review PR"},
                      {"type":"COMPLETE","id":"T-1"},
                      {"type":"UNDO"},
                      {"type":"UNSUBSCRIBE_NOTIFY"},
                      {"type":"REDO"}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.tasks[0].done").value(true))
                .andExpect(jsonPath("$.steps[2].after.tasks[0].done").value(false))
                .andExpect(jsonPath("$.current.observerDeliveries.length()").value(7))
                .andExpect(jsonPath("$.current.observerDeliveries[6]").value("AUDIT:REDO_COMPLETE:T-1"));
    }

    @Test
    void reportJsonUsesFactoryMethodAndFixedRenderingSequence() throws Exception {
        mvc().perform(post("/oop/design-patterns/reports/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"format":"CSV","sales":[{"id":"S-01","amountVnd":100},
                                              {"id":"S-02","amountVnd":20}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVnd").value(120))
                .andExpect(jsonPath("$.rowCount").value(2))
                .andExpect(jsonPath("$.executionTrace[0]").value("HEADER"))
                .andExpect(jsonPath("$.executionTrace[3]").value("SUMMARY"))
                .andExpect(jsonPath("$.content").value("saleId,amountVnd\nS-01,100\nS-02,20\nTOTAL,120"));
    }

    @Test
    void invalidHttpInputMapsOnlyThisChapterTo400() throws Exception {
        mvc().perform(post("/oop/design-patterns/pricing/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"subtotalVnd":-1,"deliveryFeeVnd":0,
                     "strategy":"NONE","order":"DISCOUNT_THEN_FEE"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        assertEquals(DesignPatternExperimentController.class,
                DesignPatternExceptionHandler.class.getAnnotation(RestControllerAdvice.class).assignableTypes()[0]);
        assertEquals("/oop/design-patterns",
                DesignPatternExperimentController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
}
