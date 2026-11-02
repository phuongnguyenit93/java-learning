package com.example.learning.casestudy.utils.group11.case9;

import com.example.learning.casestudy.domain.group11.case9.Customer9;
import com.example.learning.casestudy.port.group11.case9.Repository;

public final class Demo9Utils {
    private Demo9Utils() {
    }

    public static String customerName(Repository<Customer9, String> repository, String id) {
        return repository.find(id).orElseThrow().name();
    }
}
