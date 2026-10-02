package com.example.learning.module.networking.controller;

import com.example.learning.module.networking.service.NetworkingExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/networking/udp")
public class UdpExperimentController {

    private final NetworkingExperimentService networkingExperimentService;

    public UdpExperimentController(
            NetworkingExperimentService networkingExperimentService
    ) {
        this.networkingExperimentService = networkingExperimentService;
    }

    /** README: readme/vi/menu/4.UdpDatagrams/UdpDatagrams.md#datagram-packet-addressing */
    @GetMapping("/datagram-truncation")
    public Map<String, Object> datagramTruncation() throws IOException {
        return networkingExperimentService.udpTruncationDemo();
    }
}
