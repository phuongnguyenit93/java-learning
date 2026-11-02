package com.example.learning.casestudy.adapter.group5.case5;

import com.example.learning.casestudy.port.group5.case5.Reader;
import java.io.IOException;

public final class ServiceReader extends Reader {
    @Override public String read() throws IOException {
        try {
            return callService();
        } catch (IllegalStateException providerFailure) {
            throw new IOException("cannot read source", providerFailure);
        }
    }
    private String callService() {
        return "ready";
    }
}
