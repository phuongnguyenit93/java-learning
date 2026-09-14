package com.example.learning.module.synchronization.controller;

import com.example.learning.module.synchronization.service.SynchronizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/synchronization")
public class SynchronizationController {

    private final SynchronizationService synchronizationService;

    public SynchronizationController(SynchronizationService synchronizationService) {
        this.synchronizationService = synchronizationService;
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#synchronized-monitor
     * Purpose: Chứng minh synchronized bảo vệ một compound update bằng intrinsic monitor.
     */
    @GetMapping("/synchronized-counter")
    public Map<String, Object> synchronizedCounter() throws InterruptedException {
        return synchronizationService.synchronizedCounter();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#reentrant-lock
     * Purpose: Chứng minh cùng một Thread có thể acquire lại ReentrantLock và hold count được quản lý đúng.
     */
    @GetMapping("/reentrant-lock")
    public Map<String, Object> reentrantLock() {
        return synchronizationService.reentrantLockDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#reentrant-lock
     * Purpose: Quan sát tryLock(timeout) khi lock đang được Thread khác giữ.
     */
    @GetMapping("/try-lock")
    public Map<String, Object> tryLock() throws InterruptedException {
        return synchronizationService.tryLockDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#reentrant-lock
     * Purpose: Chứng minh lockInterruptibly() cho phép hủy Thread đang đợi explicit lock.
     */
    @GetMapping("/interruptible-lock")
    public Map<String, Object> interruptibleLock() throws InterruptedException {
        return synchronizationService.interruptibleLockDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#reentrant-lock
     * Purpose: Quan sát fair policy của ReentrantLock, gồm exception rằng untimed tryLock() có thể barge.
     */
    @GetMapping("/reentrant-lock-policy")
    public Map<String, Object> reentrantLockPolicy() {
        return synchronizationService.reentrantLockPolicyDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#read-write-lock
     * Purpose: Quan sát nhiều reader cùng chạy và writer phải đợi read lock được giải phóng.
     */
    @GetMapping("/read-write-lock")
    public Map<String, Object> readWriteLock() throws InterruptedException {
        return synchronizationService.readWriteLockDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#read-write-lock
     * Purpose: Chứng minh lock downgrading theo thứ tự write -> read -> release write.
     */
    @GetMapping("/read-write-lock-downgrade")
    public Map<String, Object> readWriteLockDowngrade() throws InterruptedException {
        return synchronizationService.readWriteLockDowngradeDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#read-write-lock-policy
     * Purpose: Quan sát default/fair policy và nuance untimed tryLock() của ReentrantReadWriteLock.
     */
    @GetMapping("/read-write-lock-policy")
    public Map<String, Object> readWriteLockPolicy() {
        return synchronizationService.readWriteLockPolicyDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#stamped-lock
     * Purpose: Làm invalid optimistic stamp rồi fallback sang read lock.
     */
    @GetMapping("/stamped-lock")
    public Map<String, Object> stampedLock() throws InterruptedException {
        return synchronizationService.stampedLockDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#stamped-lock-limitations
     * Purpose: Chứng minh StampedLock không reentrant và Lock view không cung cấp Condition.
     */
    @GetMapping("/stamped-lock-limitations")
    public Map<String, Object> stampedLockLimitations() {
        return synchronizationService.stampedLockLimitationsDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#adder-accumulator
     * Purpose: So sánh vai trò AtomicInteger, LongAdder và LongAccumulator bằng kết quả xác định.
     */
    @GetMapping("/atomic-tools")
    public Map<String, Object> atomicTools() throws InterruptedException {
        return synchronizationService.atomicToolsDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#atomic-cas
     * Purpose: Chứng minh compareAndSet, ABA và cách stamp phát hiện state đã thay đổi rồi quay về giá trị cũ.
     */
    @GetMapping("/atomic-cas")
    public Map<String, Object> atomicCas() {
        return synchronizationService.atomicCasDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#concurrent-collections
     * Purpose: Minh họa atomic map update, snapshot iteration và queue non-blocking hữu hạn.
     */
    @GetMapping("/concurrent-collections")
    public Map<String, Object> concurrentCollections() throws InterruptedException {
        return synchronizationService.concurrentCollectionsDemo();
    }

    /**
     * README: readme/vi/menu/4.Synchronization/Synchronization.md#concurrent-collections
     * Purpose: Quan sát ConcurrentSkipListMap giữ key theo sorted order khi được update đồng thời.
     */
    @GetMapping("/concurrent-skip-list")
    public Map<String, Object> concurrentSkipList() throws InterruptedException {
        return synchronizationService.concurrentSkipListDemo();
    }
}
