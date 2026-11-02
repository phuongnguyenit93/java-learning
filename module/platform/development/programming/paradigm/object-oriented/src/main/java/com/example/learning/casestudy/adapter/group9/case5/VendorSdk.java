package com.example.learning.casestudy.adapter.group9.case5;

import com.example.learning.casestudy.adapter.group9.case5.Code;
import com.example.learning.casestudy.adapter.group9.case5.VendorResponse;

public final class VendorSdk {

    public VendorResponse charge(int cents) {
        return cents > 0
        ? new VendorResponse(Code.OK_V2, "v-1")
        : new VendorResponse(Code.REJECTED_V2, "v-2");
    }
}
