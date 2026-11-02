package com.example.learning.casestudy.application.group12.case5;

import com.example.learning.casestudy.domain.group12.case5.User;
import com.example.learning.casestudy.port.group12.case5.Writer;

public class NewEditor {
    private final Writer delegate;
    public NewEditor(Writer delegate) {
        this.delegate = delegate;
    }
    public void update(User user, String value) {
        if (!user.canEdit()) throw new SecurityException("Forbidden");
        delegate.write(value);
    }
}
