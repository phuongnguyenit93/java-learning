package com.example.learning.casestudy.application.group1.case2;

import com.example.learning.casestudy.application.group1.case2.Helper;
import com.example.learning.casestudy.model.group1.case2.Order2;

public final class Factory {
    public String render(Order2 o) { return new Helper().render(o); }
}
