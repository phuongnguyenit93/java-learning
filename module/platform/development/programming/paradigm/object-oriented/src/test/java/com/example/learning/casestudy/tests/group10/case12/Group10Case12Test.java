package com.example.learning.casestudy.tests.group10.case12;


import com.example.learning.casestudy.service.Group10Service;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.junit.jupiter.api.Assertions.assertTrue;


class Group10Case12Test {

    private final Group10Service group = new Group10Service();


    @Test
    void sourceMainExecutesWithAssertionsEnabled() {

        assertTrue(Group10Case12Test.class.desiredAssertionStatus(),
        "Run source assertions with -ea");

        assertDoesNotThrow(group::executeCase12);

    }


    @Test
    void sourceExampleCanBeInvokedTwiceWithoutCrossRequestState() {

        assertDoesNotThrow(group::executeCase12);

        assertDoesNotThrow(group::executeCase12);

    }

}
