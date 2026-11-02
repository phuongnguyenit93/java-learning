package com.example.learning.casestudy.port.group4.case1;

public interface PaymentGateway { boolean capture(String orderId, long cents); }
