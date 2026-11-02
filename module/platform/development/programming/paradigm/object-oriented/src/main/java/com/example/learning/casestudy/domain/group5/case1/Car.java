package com.example.learning.casestudy.domain.group5.case1;

import com.example.learning.casestudy.port.group5.case1.EnginePowered;
import com.example.learning.casestudy.port.group5.case1.Vehicle;

public final class Car implements Vehicle, EnginePowered {
    public String move() {
        return "drive";
    }
    public String startEngine() {
        return "engine started";
    }
}
