package com.example.learning.casestudy.application.group9.case2;

import com.example.learning.casestudy.domain.group9.case2.Config;
import com.example.learning.casestudy.model.group9.case2.Client;

public class ClientFactory {
    public static Client create(Config config) {
        return new Client(config.endpoint());
    }
}
