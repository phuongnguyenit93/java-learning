package com.example.learning.oop.solid;

import com.example.learning.oop.solid.controller.SolidExperimentController;
import com.example.learning.oop.solid.exception.SolidExperimentExceptionHandler;
import com.example.learning.oop.solid.model.*;
import com.example.learning.oop.solid.service.SolidExperimentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SolidHttpTest {
    private MockMvc mvc() {
        var service = new SolidExperimentService(List.of(new NoDiscountPolicy(),
                new TenPercentPolicy(), new LoyaltyPolicy(), new BrokenNegativeDiscountPolicy()));
        return MockMvcBuilders.standaloneSetup(new SolidExperimentController(service))
                .setControllerAdvice(new SolidExperimentExceptionHandler()).build();
    }

    @Test
    void discountQuoteUsesRealPolicyAndContracts() throws Exception {
        mvc().perform(post("/oop/solid/discount/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyCode\":\"LOYALTY\",\"subtotalCents\":50000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCents").value(7500))
                .andExpect(jsonPath("$.payableCents").value(42500));
        mvc().perform(post("/oop/solid/discount/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyCode\":\"BROKEN_NEGATIVE\",\"subtotalCents\":100}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POLICY_CONTRACT"));
        mvc().perform(post("/oop/solid/discount/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyCode\":\"NONE\",\"subtotalCents\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void checkoutShowsStorageAndReceiptSideEffects() throws Exception {
        mvc().perform(post("/oop/solid/checkout/experiment").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"attempts":[
                       {"orderId":"ORD-1","policyCode":"TEN_PERCENT",
                        "lines":[{"sku":"BOOK","quantity":2,"unitPriceCents":100}]},
                       {"orderId":"ORD-2","policyCode":"BROKEN_NEGATIVE",
                        "lines":[{"sku":"BOOK","quantity":1,"unitPriceCents":100}]},
                       {"orderId":"ORD-1","policyCode":"NONE",
                        "lines":[{"sku":"PEN","quantity":1,"unitPriceCents":20}]}
                    ]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts[0].receipt.payableCents").value(180))
                .andExpect(jsonPath("$.attempts[1].rule").value("POLICY_CONTRACT"))
                .andExpect(jsonPath("$.attempts[2].rule").value("ORDER_CONFLICT"))
                .andExpect(jsonPath("$.storedOrders.length()").value(1))
                .andExpect(jsonPath("$.notices.length()").value(1))
                .andExpect(jsonPath("$.countsConsistent").value(true));

        mvc().perform(post("/oop/solid/checkout/experiment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"attempts\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertEquals(SolidExperimentController.class,
                SolidExperimentExceptionHandler.class.getAnnotation(RestControllerAdvice.class)
                        .assignableTypes()[0]);
        assertEquals("/oop/solid",
                SolidExperimentController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
}
