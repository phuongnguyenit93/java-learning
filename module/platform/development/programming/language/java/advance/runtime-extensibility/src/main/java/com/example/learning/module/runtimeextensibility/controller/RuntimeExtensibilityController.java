package com.example.learning.module.runtimeextensibility.controller;

import com.example.learning.module.runtimeextensibility.service.RuntimeExtensibilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/runtime-extensibility")
public class RuntimeExtensibilityController {

    private final RuntimeExtensibilityService runtimeExtensibilityService;

    public RuntimeExtensibilityController(
            RuntimeExtensibilityService runtimeExtensibilityService
    ) {
        this.runtimeExtensibilityService = runtimeExtensibilityService;
    }

    /** README: readme/vi/menu/3.ServiceLoaderIntegration/ServiceLoaderIntegration.md#lazy-discovery-and-cache */
    @GetMapping("/service-loader")
    public Map<String, Object> serviceLoader() {
        return runtimeExtensibilityService.serviceLoaderDemo();
    }

    /** README: readme/vi/menu/4.ProviderDiscoveryStrategy/ProviderDiscoveryStrategy.md#discovery-vs-selection */
    @GetMapping("/provider-selection")
    public Map<String, Object> providerSelection() {
        return runtimeExtensibilityService.providerSelectionDemo();
    }

    /** README: readme/vi/menu/8.PluginIsolationDesign/PluginIsolationDesign.md#class-identity-boundaries */
    @GetMapping("/classloader-identity")
    public Map<String, Object> classLoaderIdentity() {
        return runtimeExtensibilityService.classLoaderIdentityDemo();
    }

    /** README: readme/vi/menu/7.PluginLifecycle/PluginLifecycle.md#replacement-and-upgrade */
    @GetMapping("/plugin-replacement")
    public Map<String, Object> pluginReplacement() {
        return runtimeExtensibilityService.pluginReplacementDemo();
    }
}
