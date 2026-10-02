package com.example.learning.module.runtimediagnostics.controller;

import com.example.learning.module.runtimediagnostics.service.RuntimeDiagnosticsExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/runtime-diagnostics/experiments")
public class RuntimeDiagnosticsExperimentController {

    private final RuntimeDiagnosticsExperimentService experimentService;

    public RuntimeDiagnosticsExperimentController(
            RuntimeDiagnosticsExperimentService experimentService
    ) {
        this.experimentService = experimentService;
    }

    /**
     * README:
     * readme/vi/menu/3.ThreadDiagnostics/ThreadDiagnostics.md#locks-deadlocks-and-contention
     */
    @PostMapping("/thread-contention")
    public Map<String, Object> threadContention(
            @RequestParam(defaultValue = "8000")
            int durationMillis
    ) {
        return experimentService.startThreadContention(durationMillis);
    }

    /**
     * README:
     * readme/vi/menu/4.HeapGcDiagnostics/HeapGcDiagnostics.md#class-histogram
     */
    @PostMapping("/heap-retention")
    public Map<String, Object> heapRetention(
            @RequestParam(defaultValue = "8")
            int megabytes,
            @RequestParam(defaultValue = "10000")
            int durationMillis
    ) {
        return experimentService.startHeapRetention(
                megabytes,
                durationMillis
        );
    }

    /**
     * README:
     * readme/vi/menu/6.JFR/JFR.md#cpu-thread-lock-analysis
     */
    @PostMapping("/jfr-cpu-window")
    public Map<String, Object> jfrCpuWindow(
            @RequestParam(defaultValue = "5000")
            int durationMillis
    ) {
        return experimentService.startCpuWindow(durationMillis);
    }

    /**
     * README:
     * readme/vi/menu/7.ManagementJMX/ManagementJMX.md#platform-management-surfaces
     */
    @GetMapping("/mxbeans/snapshot")
    public Map<String, Object> mxBeanSnapshot() {
        return experimentService.managementSnapshot();
    }
}
