package com.example.learning.casestudy.port.group9.case3;

import com.example.learning.casestudy.domain.group9.case3.Env;
import com.example.learning.casestudy.model.group9.case3.DatabaseClient;
import com.example.learning.casestudy.model.group9.case3.MessagingClient;

public interface ClientFamily {
    public Env env();
    public DatabaseClient database();
    public MessagingClient messaging();
}
