package com.example.learning.casestudy.port.group12.case7;

import com.example.learning.casestudy.domain.group12.case7.Order7;

public interface Listener {
    void onCommitted(Order7 order);
}
