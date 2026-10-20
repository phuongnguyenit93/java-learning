package com.example.learning.oop.inheritance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.PayrollRequest;
import com.example.learning.oop.inheritance.model.Employee;
import com.example.learning.oop.inheritance.service.PayrollInheritanceService;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class PayrollInheritanceServiceTest {
    private final PayrollInheritanceService service = new PayrollInheritanceService();

    @Test
    void salariedEmployeeInheritsValidatedIdentityAndDispatchesFixedPay() {
        var output = service.calculate(new PayrollRequest(
                "salaried", " E-01 ", " Ada ", 20_000_000L, 0, 0));
        assertEquals("E-01", output.employeeId());
        assertEquals("Ada", output.employeeName());
        assertEquals("SALARIED", output.kind());
        assertEquals("Employee", output.declaredType());
        assertEquals("Salaried", output.runtimeClass());
        assertEquals("FIXED_MONTHLY", output.calculation());
        assertEquals(20_000_000L, output.monthlyPayVnd());
        assertTrue(output.simulatedOnly());
    }

    @Test
    void hourlyOverrideCalculatesPayThroughSuperclassReference() {
        Employee worker = new Employee.Hourly("H-01", "Grace", 130_000L, 160);
        assertEquals(20_800_000L, worker.monthlyPayVnd());
        var result = service.calculate(new PayrollRequest("HOURLY", "H-01",
                "Grace", 0L, 130_000L, 160));
        assertEquals(20_800_000L, result.monthlyPayVnd());
        assertEquals("Hourly", result.runtimeClass());
    }

    @Test
    void rejectsInvalidInputsBeforeSalaryCalculation() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(new PayrollRequest("OTHER", "E", "Name", 1, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(new PayrollRequest("SALARIED", "", "Name", 1, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(new PayrollRequest("SALARIED", "E", "Name", -1, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(new PayrollRequest("SALARIED", "E", "Name", 1, 2, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(new PayrollRequest("HOURLY", "E", "Name", 1, 20, 10)));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(new PayrollRequest("HOURLY", "E", "Name", 0, 20, 745)));
    }

    @Test
    void exactArithmeticRejectsOverflowRatherThanWrapping() {
        assertThrows(ArithmeticException.class,
                () -> service.calculate(new PayrollRequest(
                        "HOURLY", "E", "Name", 0, Long.MAX_VALUE, 2)));
    }

    @Test
    void parentIsAbstractAndKeyStateIsNotExposedForSubclassesToMutate() throws Exception {
        assertTrue(Modifier.isAbstract(Employee.class.getModifiers()));
        assertTrue(Modifier.isPrivate(Employee.class.getDeclaredField("employeeId").getModifiers()));
        assertTrue(Modifier.isFinal(Employee.class.getDeclaredMethod("employeeId").getModifiers()));
        assertEquals(Employee.class, Employee.Hourly.class.getSuperclass());
    }
}
