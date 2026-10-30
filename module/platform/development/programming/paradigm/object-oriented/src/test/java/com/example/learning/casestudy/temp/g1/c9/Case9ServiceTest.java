package com.example.learning.casestudy.temp.g1.c9;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Case9ServiceTest {

    private final Case9Service service = new Case9Service();

    @Test
    void cpuWorkloadPerformsTheOriginalArithmetic() {
        assertEquals(0, service.compute(0));
        assertEquals(1, service.compute(2));
        assertEquals(30, service.compute(5));

        Case9Service.WorkloadResult result = service.execute("cpu", 5);
        assertEquals("cpu", result.mode());
        assertEquals(5, result.rounds());
        assertEquals(30, result.result());
    }

    @Test
    void allocationWorkloadCountsActualAllocatedBytes() {
        assertEquals(0, service.allocate(0));
        assertEquals(1024, service.allocate(1));
        assertEquals(4096, service.allocate(4));

        Case9Service.WorkloadResult result = service.execute("allocation", 3);
        assertEquals("allocation", result.mode());
        assertEquals(3, result.rounds());
        assertEquals(3072, result.result());
    }

    @Test
    void unknownWorkloadOrMissingModeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.execute("unknown", 5));
        assertThrows(IllegalArgumentException.class, () -> service.execute(null, 5));
        assertThrows(IllegalArgumentException.class, () -> service.execute("CPU", 5));
    }

    @Test
    void invalidRoundCountsAndUnproductiveCpuRequestAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.execute("cpu", 0));
        assertThrows(IllegalArgumentException.class, () -> service.execute("allocation", -1));
        assertThrows(IllegalArgumentException.class, () -> service.execute("cpu", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute("cpu", Case9Service.MAX_CPU_ROUNDS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute("allocation", Case9Service.MAX_ALLOCATION_ROUNDS + 1));
    }
}
