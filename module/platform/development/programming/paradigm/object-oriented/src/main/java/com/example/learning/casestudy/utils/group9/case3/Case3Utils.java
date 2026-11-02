package com.example.learning.casestudy.utils.group9.case3;

import com.example.learning.casestudy.application.group9.case3.Application;
import com.example.learning.casestudy.port.group9.case3.ClientFamily;

public final class Case3Utils {
    private Case3Utils() {}

    public static Application build(ClientFamily family) {
        Application app = new Application(family.database(), family.messaging());
        if (app.db().env() != family.env()) throw new IllegalStateException();
        return app;
    }

}
