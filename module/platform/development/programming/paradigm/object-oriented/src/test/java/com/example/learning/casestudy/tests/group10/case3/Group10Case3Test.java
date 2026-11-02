package com.example.learning.casestudy.tests.group10.case3;


import com.example.learning.casestudy.service.Group10Service;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.junit.jupiter.api.Assertions.assertTrue;


class Group10Case3Test {

    private final Group10Service group = new Group10Service();


    @Test
    void sourceMainExecutesWithAssertionsEnabled() {

        assertTrue(Group10Case3Test.class.desiredAssertionStatus(),
        "Run source assertions with -ea");

        assertDoesNotThrow(group::executeCase3);

    }


    @Test
    void sourceExampleCanBeInvokedTwiceWithoutCrossRequestState() {

        assertDoesNotThrow(group::executeCase3);

        assertDoesNotThrow(group::executeCase3);

    }

}
