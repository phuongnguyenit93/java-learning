package com.example.learning.module.web.client.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.client.support.RestTemplateAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HttpClientExperimentService {

    private static final int LOOPBACK_TIMEOUT_MILLIS = 2_000;

    private volatile RestClient restClient;
    private volatile EchoHttpService restClientProxy;
    private volatile EchoHttpService restTemplateProxy;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeLocalClients(ApplicationReadyEvent event) {
        ServletWebServerApplicationContext context =
                (ServletWebServerApplicationContext) event.getApplicationContext();
        String contextPath = context.getServletContext().getContextPath();
        String baseUrl = "http://127.0.0.1:"
                + context.getWebServer().getPort()
                + (contextPath == null ? "" : contextPath);

        RestClient configuredRestClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(loopbackRequestFactory())
                .defaultStatusHandler(
                        HttpStatusCode::isError,
                        (request, response) -> {
                            throw new IllegalStateException(
                                    "configured-handler:" + response.getStatusCode().value()
                            );
                        }
                )
                .build();

        RestTemplate configuredRestTemplate = new RestTemplate(loopbackRequestFactory());
        configuredRestTemplate.setUriTemplateHandler(
                new DefaultUriBuilderFactory(baseUrl)
        );

        this.restClient = configuredRestClient;
        this.restClientProxy = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(configuredRestClient))
                .build()
                .createClient(EchoHttpService.class);
        this.restTemplateProxy = HttpServiceProxyFactory
                .builderFor(RestTemplateAdapter.create(configuredRestTemplate))
                .build()
                .createClient(EchoHttpService.class);
    }

    private static SimpleClientHttpRequestFactory loopbackRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(LOOPBACK_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(LOOPBACK_TIMEOUT_MILLIS);
        return requestFactory;
    }

    public Map<String, Object> restClientStatusHandling() {
        RestClient client = requireRestClient();

        Map<?, ?> okBody = client.get()
                .uri("/spring-web/support/ok")
                .retrieve()
                .body(Map.class);

        String retrieveFailure;
        try {
            client.get()
                    .uri("/spring-web/support/missing")
                    .retrieve()
                    .toBodilessEntity();
            retrieveFailure = "none";
        }
        catch (IllegalStateException exception) {
            retrieveFailure = exception.getMessage();
        }

        int exchangeStatus = client.get()
                .uri("/spring-web/support/missing")
                .exchange((request, response) -> response.getStatusCode().value());

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("okBody", okBody);
        evidence.put("retrieveFailure", retrieveFailure);
        evidence.put("exchangeStatus", exchangeStatus);
        evidence.put(
                "exchangeBypassedConfiguredStatusHandler",
                exchangeStatus == 404 && !"none".equals(retrieveFailure)
        );
        return evidence;
    }

    public Map<String, Object> httpServiceAdapters(String id) {
        EchoHttpService restClientService = requireProxy(restClientProxy);
        EchoHttpService restTemplateService = requireProxy(restTemplateProxy);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put(
                "restClientAdapter",
                restClientService.echo(id, "proxy", "rest-client-adapter")
        );
        evidence.put(
                "restTemplateAdapter",
                restTemplateService.echo(id, "proxy", "rest-template-adapter")
        );
        evidence.put("sameInterfaceType", EchoHttpService.class.getName());
        return evidence;
    }

    private RestClient requireRestClient() {
        RestClient client = this.restClient;
        if (client == null) {
            throw new IllegalStateException("Local HTTP experiment client is not initialized");
        }
        return client;
    }

    private static EchoHttpService requireProxy(EchoHttpService proxy) {
        if (proxy == null) {
            throw new IllegalStateException("HTTP Service experiment proxy is not initialized");
        }
        return proxy;
    }
}
