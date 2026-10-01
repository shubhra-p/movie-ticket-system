package com.shubh.movietickets.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 Programmatic route configuration for Spring Cloud Gateway.

 All routes use the `lb://` scheme which instructs Spring Cloud LoadBalancer
 to resolve the service name via Eureka and distribute requests across
 registered instances (client-side load balancing).

 Path rewrites are not needed because downstream services already serve
 under the same /api/{resource} paths.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                /* Users Service — auth, registration, user management */
                .route("users-service", r -> r
                        .path("/api/users/**")
                        .uri("lb://users-service"))

                /* Theaters Service — theater/screen/seat-layout CRUD */
                .route("theaters-service", r -> r
                        .path("/api/theaters/**")
                        .uri("lb://theaters-service"))

                /* Movies Service — movie catalog and schedules */
                .route("movies-service", r -> r
                        .path("/api/movies/**", "/api/schedules/**")
                        .uri("lb://movies-service"))

                /* Seats Service — seat availability, locking */
                .route("seats-service", r -> r
                        .path("/api/seats/**")
                        .uri("lb://seats-service"))

                /* Tickets Service — booking saga */
                .route("tickets-service", r -> r
                        .path("/api/bookings/**")
                        .uri("lb://tickets-service"))

                /* Payment Service — mock payment gateway */
                .route("payment-service", r -> r
                        .path("/api/payments/**")
                        .uri("lb://payment-service"))

                .build();
    }
}
