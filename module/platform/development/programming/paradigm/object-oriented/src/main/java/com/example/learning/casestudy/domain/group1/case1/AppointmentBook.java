package com.example.learning.casestudy.domain.group1.case1;

import com.example.learning.casestudy.domain.group1.case1.Patient;
import java.util.HashMap;
import java.util.Map;

public final class AppointmentBook {
    private final Map<String, String> appointments = new HashMap<>();
    public void book(Patient patient, String slot) {
        if (patient.id().isBlank() || slot.isBlank())
            throw new IllegalArgumentException("invalid booking");
        appointments.put(patient.id(), slot);
    }
    public String slotFor(String patientId) { return appointments.get(patientId); }
}
