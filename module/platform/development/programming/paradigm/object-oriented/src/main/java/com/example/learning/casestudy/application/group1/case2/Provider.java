package com.example.learning.casestudy.application.group1.case2;

import com.example.learning.casestudy.application.group1.case2.Factory;
import com.example.learning.casestudy.model.group1.case2.Order2;

public final class Provider {
    public String render(Order2 o) { return new Factory().render(o); }
}
