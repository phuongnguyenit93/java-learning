package com.example.learning.module.advanced.introduction.contract;

import java.util.concurrent.atomic.AtomicInteger;

public class DefaultUsageTracked implements UsageTracked {

    private final AtomicInteger useCount = new AtomicInteger();

    @Override
    public void incrementUseCount() {
        useCount.incrementAndGet();
    }

    @Override
    public int getUseCount() {
        return useCount.get();
    }

    @Override
    public void resetUseCount() {
        useCount.set(0);
    }
}
