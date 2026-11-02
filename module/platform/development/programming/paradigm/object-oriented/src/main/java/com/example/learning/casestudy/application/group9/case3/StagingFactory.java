package com.example.learning.casestudy.application.group9.case3;

import com.example.learning.casestudy.domain.group9.case3.Env;
import com.example.learning.casestudy.model.group9.case3.DatabaseClient;
import com.example.learning.casestudy.model.group9.case3.MessagingClient;
import com.example.learning.casestudy.port.group9.case3.ClientFamily;

public record StagingFactory() implements ClientFamily {
    public Env env() { return Env.STAGING; }
    public DatabaseClient database() { return new DatabaseClient(env()); }
    public MessagingClient messaging() { return new MessagingClient(env()); }
}
