package com.example.learning.casestudy.application.group9.case10;

import com.example.learning.casestudy.domain.group9.case10.ClientConfig;

public class ConfigBuilder {
    private boolean tls;
    private boolean plainHttp;
    private String host;
    public ConfigBuilder tls(boolean value) { tls = value; return this; }
    public ConfigBuilder plainHttp(boolean value) { plainHttp = value; return this; }
    public ConfigBuilder host(String value) { host = value; return this; }
    public ClientConfig build() {
        if (tls == plainHttp)
            throw new IllegalStateException("Choose exactly one transport");
        if (host == null || host.isBlank())
            throw new IllegalStateException("Host is required");
        return new ClientConfig(tls, plainHttp, host);
    }
}
