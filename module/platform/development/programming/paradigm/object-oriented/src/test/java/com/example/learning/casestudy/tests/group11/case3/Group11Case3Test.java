package com.example.learning.casestudy.tests.group11.case3;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case3.BadBase;
import com.example.learning.casestudy.domain.group11.case3.BadSubtype;
import com.example.learning.casestudy.domain.group11.case3.Identity;
import com.example.learning.casestudy.domain.group11.case3.NormalCustomer;
import com.example.learning.casestudy.domain.group11.case3.PremiumCustomer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group11Case3Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase3();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        BadBase base = new BadBase("X");
        BadBase subtype = new BadSubtype("X");
        assertTrue(base.equals(subtype));
        assertFalse(subtype.equals(base));
        Identity shared = new Identity("X");
        NormalCustomer normal = new NormalCustomer(shared, "A");
        PremiumCustomer premium = new PremiumCustomer(shared, 2);
        assertEquals(normal.identity(), premium.identity());
        assertEquals(premium.identity(), normal.identity());
        assertNotEquals(normal, premium);
    }
}
