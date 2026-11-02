package com.example.learning.casestudy.application.group1.case1;

import com.example.learning.casestudy.domain.group1.case1.AppointmentBook;
import com.example.learning.casestudy.domain.group1.case1.Patient;
import com.example.learning.casestudy.port.group1.case1.Billing;
import com.example.learning.casestudy.port.group1.case1.Messenger;

public final class HospitalUseCase {
    private final AppointmentBook appointments;
    private final Billing billing;
    private final Messenger messenger;
    public HospitalUseCase(AppointmentBook a, Billing b, Messenger m) {
        appointments = a;
        billing = b;
        messenger = m;
    }
    public long book(Patient patient, String slot, long baseFee) {
        appointments.book(patient, slot);
        messenger.notifyPatient(patient.id());
        return billing.charge(baseFee);
    }
}
