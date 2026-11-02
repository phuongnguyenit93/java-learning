package com.example.learning.casestudy.application.group9.case3;

import com.example.learning.casestudy.model.group9.case3.DatabaseClient;
import com.example.learning.casestudy.model.group9.case3.MessagingClient;

public record Application(DatabaseClient db, MessagingClient message) {
    public Application {
        if (db.env() != message.env())
            throw new IllegalArgumentException("Mixed environments");
    }
}
