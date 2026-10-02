package com.example.learning.module.networking.controller;

import com.example.learning.module.networking.service.NetworkingExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/networking/tcp")
public class TcpExperimentController {

    private final NetworkingExperimentService networkingExperimentService;

    public TcpExperimentController(
            NetworkingExperimentService networkingExperimentService
    ) {
        this.networkingExperimentService = networkingExperimentService;
    }

    /** README: readme/vi/menu/3.TcpSockets/TcpSockets.md#tcp-message-framing */
    @GetMapping("/stream-framing")
    public Map<String, Object> streamFraming() throws Exception {
        return networkingExperimentService.tcpFramingDemo();
    }
}
