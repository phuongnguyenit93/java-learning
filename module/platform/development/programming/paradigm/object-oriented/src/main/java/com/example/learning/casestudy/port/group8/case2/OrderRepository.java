package com.example.learning.casestudy.port.group8.case2;

import com.example.learning.casestudy.domain.group8.case2.Order2;
import java.util.Optional;

public interface OrderRepository { public Optional<Order2> find(String id); }
