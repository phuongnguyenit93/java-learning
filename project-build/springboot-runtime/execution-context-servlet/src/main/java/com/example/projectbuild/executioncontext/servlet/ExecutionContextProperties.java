package com.example.projectbuild.executioncontext.servlet;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "execution-context")
public class ExecutionContextProperties {

    private boolean enabled = true;
    private boolean captureLogs = true;
    private boolean captureSystemStreams = true;
    private boolean queryEnabled = true;
    private boolean exportEnabled = true;
    private int maxHistory = 100;
    private int maxBodyLength = 65_536;
    private String responseHeader = "X-Execution-Id";
    private List<String> ignoredPathPrefixes = new ArrayList<>(List.of(
            "/execution-context",
            "/v3/api-docs",
            "/swagger-ui",
            "/actuator"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCaptureLogs() {
        return captureLogs;
    }

    public void setCaptureLogs(boolean captureLogs) {
        this.captureLogs = captureLogs;
    }

    public boolean isCaptureSystemStreams() {
        return captureSystemStreams;
    }

    public void setCaptureSystemStreams(boolean captureSystemStreams) {
        this.captureSystemStreams = captureSystemStreams;
    }

    public boolean isQueryEnabled() {
        return queryEnabled;
    }

    public void setQueryEnabled(boolean queryEnabled) {
        this.queryEnabled = queryEnabled;
    }

    public boolean isExportEnabled() {
        return exportEnabled;
    }

    public void setExportEnabled(boolean exportEnabled) {
        this.exportEnabled = exportEnabled;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    public void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }

    public String getResponseHeader() {
        return responseHeader;
    }

    public void setResponseHeader(String responseHeader) {
        this.responseHeader = responseHeader;
    }

    public List<String> getIgnoredPathPrefixes() {
        return ignoredPathPrefixes;
    }

    public void setIgnoredPathPrefixes(List<String> ignoredPathPrefixes) {
        this.ignoredPathPrefixes = ignoredPathPrefixes == null
                ? new ArrayList<>()
                : new ArrayList<>(ignoredPathPrefixes);
    }
}
