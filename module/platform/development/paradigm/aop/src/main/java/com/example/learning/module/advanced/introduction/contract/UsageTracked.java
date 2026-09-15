package com.example.learning.module.advanced.introduction.contract;

public interface UsageTracked {

    void incrementUseCount();

    int getUseCount();

    void resetUseCount();
}
