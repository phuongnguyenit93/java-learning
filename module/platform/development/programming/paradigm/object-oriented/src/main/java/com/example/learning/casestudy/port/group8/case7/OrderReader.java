package com.example.learning.casestudy.port.group8.case7;

import com.example.learning.casestudy.domain.group8.case7.Order7;
import java.util.Optional;

public interface OrderReader { public Optional<Order7> find(String id); }
