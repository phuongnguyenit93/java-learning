package com.example.learning.module.basic.controller;

import com.example.learning.module.basic.service.DaemonThreadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/daemon")
public class DaemonThreadController {

    private final DaemonThreadService daemonThreadService;

    public DaemonThreadController(DaemonThreadService daemonThreadService) {
        this.daemonThreadService = daemonThreadService;
    }

    /**
     * README: readme/vi/menu/1.Basic/Basic.md#daemon-thread
     * Purpose: Quan sát daemon flag và quy tắc child Thread kế thừa daemon status.
     */
    @GetMapping("/inspect")
    public Map<String, Object> inspectDaemonRules() {
        return daemonThreadService.inspectDaemonRules();
    }
}
