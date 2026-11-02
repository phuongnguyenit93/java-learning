package com.example.learning.casestudy.application.group9.case4;

import com.example.learning.casestudy.port.group9.case4.Service;
import java.util.List;

public final class AuditDecorator4 implements Service {
    public final Service delegate;
    public final List<String> audit;
    public AuditDecorator4(Service delegate, List<String> audit) {
        this.delegate = delegate;
        this.audit = audit;
    }
    public String execute() {
        try {
            String result = delegate.execute();
            audit.add("success");
            return result;
        } catch (RuntimeException original) {
            audit.add("failed:" + original.getClass().getSimpleName());
            throw original;
        }
    }
}
