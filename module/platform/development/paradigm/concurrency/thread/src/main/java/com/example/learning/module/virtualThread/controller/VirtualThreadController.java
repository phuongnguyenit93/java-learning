package com.example.learning.module.virtualThread.controller;

import com.example.learning.module.virtualThread.service.VirtualThreadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/virtual-thread")
public class VirtualThreadController {

    private final VirtualThreadService virtualThreadService;

    public VirtualThreadController(VirtualThreadService virtualThreadService) {
        this.virtualThreadService = virtualThreadService;
    }

    /** README: readme/vi/menu/11.Virtual_Thread/VirtualThread.md#virtual-thread-basic */
    @GetMapping("/basic")
    public Map<String, Object> basic() throws InterruptedException {
        return virtualThreadService.basicDemo();
    }

    /**
     * README: readme/vi/menu/11.Virtual_Thread/VirtualThread.md#virtual-thread-creation
     * Purpose: So sánh Thread.startVirtualThread() và Thread.ofVirtual().start() bằng hai Virtual Thread thật.
     */
    @GetMapping("/creation-api")
    public Map<String, Object> creationApi() throws InterruptedException {
        return virtualThreadService.creationApisDemo();
    }

    /** README: readme/vi/menu/11.Virtual_Thread/VirtualThread.md#virtual-executor */
    @GetMapping("/blocking-scale")
    public Map<String, Object> blockingScale() throws Exception {
        return virtualThreadService.blockingScaleDemo();
    }

    /** README: readme/vi/menu/11.Virtual_Thread/VirtualThread.md#resource-limits */
    @GetMapping("/limited-resource")
    public Map<String, Object> limitedResource() throws Exception {
        return virtualThreadService.limitedResourceDemo();
    }

    /**
     * README: readme/vi/menu/11.Virtual_Thread/VirtualThread.md#virtual-thread-thread-local
     * Purpose: Minh họa ThreadLocal vẫn là per-thread state và cần cleanup dù Thread là virtual.
     */
    @GetMapping("/thread-local")
    public Map<String, Object> threadLocal() throws InterruptedException {
        return virtualThreadService.threadLocalDemo();
    }
}
