package com.example.learning.casestudy.utils.group9.case2;

import com.example.learning.casestudy.application.group9.case2.ClientFactory;
import com.example.learning.casestudy.domain.group9.case2.Config;
import com.example.learning.casestudy.model.group9.case2.Client;

public final class Case2Utils {
    private Case2Utils() {}

    public static void contract(Config config, String expectedEndpoint) {
        Client client = ClientFactory.create(config);
        if (!client.endpoint().equals(expectedEndpoint)) throw new AssertionError();
    }

}
