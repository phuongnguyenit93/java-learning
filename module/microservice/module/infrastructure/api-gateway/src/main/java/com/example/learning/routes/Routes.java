package com.example.learning.routes;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.web.servlet.function.*;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class Routes {

    @Bean
    public RouterFunction<ServerResponse> subServicesRoute() {
        return GatewayRouterFunctions.route("sub_services_proxy")
                // Cấu trúc: /{tên-service-trên-eureka}/**
                .route(GatewayRequestPredicates.path("/{serviceName}/api/**"), HandlerFunctions.http())
                // 2. TOKEN RELAY: Lấy Token từ Session và nhét vào Header Authorization
                .filter(TokenRelayFilterFunctions.tokenRelay())
                .before(request -> {
                    String serviceName = request.pathVariable("serviceName");
                    // Gán ID và URI cho LoadBalancer của Gateway MVC
                    MvcUtils.putAttribute(request, MvcUtils.GATEWAY_ROUTE_ID_ATTR, serviceName);
                    return request;
                })
                .filter((request, next) -> {
                    String serviceName = request.pathVariable("serviceName");
                    return LoadBalancerFilterFunctions.lb(serviceName).apply(next).handle(request);
                })
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> swaggerRouting() {
        return GatewayRouterFunctions.route("dynamic_swagger")
                .route(
                        GatewayRequestPredicates.path("/v3/api-docs/{serviceName}")
                                // Loại trừ: Chỉ chạy nếu serviceName KHÔNG PHẢI là "swagger-config"
                                .and(req -> !"{serviceName}".equals(req.pathVariable("serviceName"))
                                        && !"swagger-config".equals(req.pathVariable("serviceName"))),
                        HandlerFunctions.http()
                )
                .before(BeforeFilterFunctions.setPath("{serviceName}/v3/api-docs"))
                // Thay vì dùng lb("{serviceName}"), ta dùng Filter để giải quyết tên service thủ công
                .filter((request, next) -> {
                    String serviceName = request.pathVariable("serviceName");

                    // 1. Gán Route ID để hiển thị trên Monitoring (Prometheus/Grafana)
                    MvcUtils.putAttribute(request, MvcUtils.GATEWAY_ROUTE_ID_ATTR, serviceName);

                    // Gọi filter LoadBalancer với tên cụ thể đã lấy được
                    return LoadBalancerFilterFunctions.lb(serviceName).apply(next).handle(request);
                })
                .build();
    }

}
