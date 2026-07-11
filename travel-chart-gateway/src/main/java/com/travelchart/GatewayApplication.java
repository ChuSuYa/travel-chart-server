package com.travelchart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("user-service", r -> r
                .path("/api/user/**", "/api/home/**", "/api/traveler/**")
                .uri("lb://travel-chart-user"))
            .route("plan-service", r -> r
                .path("/api/plan/**")
                .uri("lb://travel-chart-plan"))
            .route("weather-service", r -> r
                .path("/api/weather/**")
                .uri("lb://travel-chart-weather"))
            .route("social-service", r -> r
                .path("/api/social/**")
                .uri("lb://travel-chart-social"))
            .route("search-service", r -> r
                .path("/api/search/**")
                .uri("lb://travel-chart-search"))
            .route("manage-service", r -> r
                .path("/api/manage/**")
                .uri("lb://travel-chart-manage"))
            .build();
    }
}
