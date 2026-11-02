package com.example.learning.casestudy.tests.group11.case7;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case7.User;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group11Case7Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase7();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        String token = "tok-SECRET-123";
        String pan = "4111111111111111";
        User user = new User("U-1", "VIEWER", token, pan);
        String plain = user.toString();
        String structured = user.safeLogFields().toString();
        assertTrue(plain.contains("U-1"));
        assertTrue(structured.contains("VIEWER"));
        for (String value : List.of(plain, structured)) {
            assertFalse(value.contains(token));
            assertFalse(value.contains(pan));
            assertFalse(value.contains("accessToken"));
            assertFalse(value.contains("cardNumber"));
        }
    }
}
