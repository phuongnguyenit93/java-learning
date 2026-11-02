package com.example.learning.casestudy.port.group4.case2;

import com.example.learning.casestudy.model.group4.case2.Order;
import java.util.Optional;

public interface OrderLookup { Optional<Order> find(String id); }
