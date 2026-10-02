package com.example.learning.module.dynamicruntime.controller;

import com.example.learning.module.dynamicruntime.service.DynamicRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dynamic-runtime")
public class DynamicRuntimeController {

    private final DynamicRuntimeService dynamicRuntimeService;

    public DynamicRuntimeController(DynamicRuntimeService dynamicRuntimeService) {
        this.dynamicRuntimeService = dynamicRuntimeService;
    }

    /** README: readme/vi/menu/4.MethodHandleAdaptation/MethodHandleAdaptation.md#method-handle-type-adaptation */
    @GetMapping("/method-handle")
    public Map<String, Object> methodHandle() {
        return dynamicRuntimeService.methodHandleDemo();
    }

    /** README: readme/vi/menu/3.LookupAccess/LookupAccess.md#lookup-context */
    @GetMapping("/lookup")
    public Map<String, Object> lookup() {
        return dynamicRuntimeService.lookupDemo();
    }

    /** README: readme/vi/menu/5.CallSite/CallSite.md#call-site-target-updates */
    @GetMapping("/call-site")
    public Map<String, Object> callSite() {
        return dynamicRuntimeService.callSiteDemo();
    }

    /** README: readme/vi/menu/7.VarHandle/VarHandle.md#var-handle-atomic-updates */
    @GetMapping("/var-handle")
    public Map<String, Object> varHandle() {
        return dynamicRuntimeService.varHandleDemo();
    }
}
