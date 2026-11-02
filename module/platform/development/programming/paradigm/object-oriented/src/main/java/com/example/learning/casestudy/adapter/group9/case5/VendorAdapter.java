package com.example.learning.casestudy.adapter.group9.case5;

import com.example.learning.casestudy.adapter.group9.case5.VendorResponse;
import com.example.learning.casestudy.adapter.group9.case5.VendorSdk;
import com.example.learning.casestudy.domain.group9.case5.DomainStatus;
import com.example.learning.casestudy.domain.group9.case5.PaymentResult;
import com.example.learning.casestudy.port.group9.case5.PaymentPort;

public final class VendorAdapter implements PaymentPort {
    private final VendorSdk sdk;
    public VendorAdapter(VendorSdk sdk) { this.sdk = sdk; }
    public PaymentResult pay(int cents) {
        VendorResponse response = sdk.charge(cents);
        DomainStatus status = switch (response.code()) {
            case OK_V2 -> DomainStatus.APPROVED;
            case REJECTED_V2 -> DomainStatus.DECLINED;
        };
        return new PaymentResult(status, response.vendorTxnId());
    }
}
