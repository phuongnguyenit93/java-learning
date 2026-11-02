package com.example.learning.casestudy.port.group8.case3;

import com.example.learning.casestudy.domain.group8.case3.Event;

public interface Outbox { public void enqueue(Event event); }
