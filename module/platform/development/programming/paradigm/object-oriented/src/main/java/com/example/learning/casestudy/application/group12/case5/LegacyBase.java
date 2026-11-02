package com.example.learning.casestudy.application.group12.case5;

import com.example.learning.casestudy.domain.group12.case5.User;
import com.example.learning.casestudy.port.group12.case5.Repository;

public class LegacyBase {
    protected final Repository repository;
    public LegacyBase(Repository repository) {
        this.repository = repository;
    }
    public final void update(User user, String value) {
        if (!user.canEdit()) throw new SecurityException("Forbidden");
        doUpdate(value);
    }
    protected void doUpdate(String value) {
        repository.update(value);
    }
}
