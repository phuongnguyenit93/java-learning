package com.example.learning.oop.class_design;

import com.example.learning.oop.class_design.controller.ClassDesignExperimentsController;
import com.example.learning.oop.class_design.exception.ClassDesignExceptionHandler;
import com.example.learning.oop.class_design.service.CheckoutScenarioService;
import com.example.learning.oop.class_design.service.LibraryScenarioService;
import com.example.learning.oop.class_design.service.TransferScenarioService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClassDesignHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new ClassDesignExperimentsController(
                new LibraryScenarioService(), new CheckoutScenarioService(), new TransferScenarioService()))
                .setControllerAdvice(new ClassDesignExceptionHandler())
                .build();
    }

    @Test
    void libraryScenarioShowsAllowedAndRejectedTransitionsWithoutLeakingAcrossRequests()
            throws Exception {
        String request = """
                {"maxActiveLoans":1,"loanDays":14,
                 "copies":[{"copyId":"C-1","title":"Effective Java"}],
                 "actions":[
                   {"type":"BORROW","borrowerId":"M-1","copyId":"C-1","onDate":"2026-10-20"},
                   {"type":"QUERY"},
                   {"type":"BORROW","borrowerId":"M-2","copyId":"C-1","onDate":"2026-10-21"},
                   {"type":"RETURN","copyId":"C-1","onDate":"2026-11-05"}]}
                """;
        for (int i = 0; i < 2; i++) {
            mvc().perform(post("/oop/class-design/library/experiment")
                    .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.steps[0].outcome").value("BORROWED"))
                    .andExpect(jsonPath("$.steps[1].loans[0].dueOn").value("2026-11-03"))
                    .andExpect(jsonPath("$.steps[2].accepted").value(false))
                    .andExpect(jsonPath("$.steps[2].outcome").value("COPY_ALREADY_BORROWED"))
                    .andExpect(jsonPath("$.finalLoans[0].lateFeeCents").value(200));
        }
    }

    @Test
    void checkoutAndTransferReturnCalculatedResultsNotFixedText() throws Exception {
        mvc().perform(post("/oop/class-design/checkout/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"orderId":"O-7","receiptAddress":"a@example.org","discountPercent":25,
                     "lines":[{"sku":"JAVA","quantity":3,"unitPriceCents":1001}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotalCents").value(3003))
                .andExpect(jsonPath("$.discountCents").value(750))
                .andExpect(jsonPath("$.totalCents").value(2253))
                .andExpect(jsonPath("$.receiptOutbox[0].amountCents").value(2253));

        mvc().perform(post("/oop/class-design/transfers/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fromAccountId":"A","toAccountId":"B","currency":"USD",
                     "fromBalanceCents":900,"toBalanceCents":100,"amountCents":450}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAfterCents").value(450))
                .andExpect(jsonPath("$.toAfterCents").value(550));
    }

    @Test
    void malformedCheckoutReturns400AndInsufficientFundsReturnsScoped409() throws Exception {
        mvc().perform(post("/oop/class-design/checkout/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"orderId":"O","receiptAddress":"a@b","discountPercent":101,
                     "lines":[{"sku":"A","quantity":1,"unitPriceCents":100}]}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        mvc().perform(post("/oop/class-design/transfers/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fromAccountId":"A","toAccountId":"B","currency":"USD",
                     "fromBalanceCents":100,"toBalanceCents":200,"amountCents":101}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
        assertEquals(ClassDesignExperimentsController.class,
                ClassDesignExceptionHandler.class.getAnnotation(RestControllerAdvice.class)
                        .assignableTypes()[0]);
    }
}
