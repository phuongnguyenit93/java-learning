package com.example.learning.controller;

import com.example.learning.model.ControlCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.example.learning.service.ControlProducer;

@RestController
public class ControlController {
    @Autowired
    ControlProducer controlProducer;

    @PostMapping("/control-consumer")
    public void sendControlConsumer(@RequestBody ControlCommand cmd) {
        controlProducer.controlConsumer(cmd);
    }
}
