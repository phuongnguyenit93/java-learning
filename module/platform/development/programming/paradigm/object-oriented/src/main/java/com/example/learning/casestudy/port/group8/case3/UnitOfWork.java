package com.example.learning.casestudy.port.group8.case3;


public interface UnitOfWork { public void inTransaction(Runnable work); }
