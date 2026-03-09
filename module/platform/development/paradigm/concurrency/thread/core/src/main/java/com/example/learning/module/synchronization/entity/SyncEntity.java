package com.example.learning.module.synchronization.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
@Component
public class SyncEntity {
    private int unsafeCount = 0;
    private int safeCountWithSync = 0;
    private final AtomicInteger atomicCount = new AtomicInteger(0);

    public void incrementUnsafe() { unsafeCount++; }
    public synchronized void incrementSafeSync() { safeCountWithSync++; }
    public void incrementAtomic() { atomicCount.incrementAndGet(); }

    public void reset() {
        unsafeCount = 0;
        safeCountWithSync = 0;
        atomicCount.set(0);
    }
}
