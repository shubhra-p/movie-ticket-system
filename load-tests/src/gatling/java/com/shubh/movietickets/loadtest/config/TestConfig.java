package com.shubh.movietickets.loadtest.config;

import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * Shared HTTP configuration and constants for all load test simulations.
 * <p>
 * Tests hit the API Gateway (port 8080) to exercise the full path:
 * Gateway JWT filter → Eureka discovery → downstream service → DB/Redis/Kafka
 */
public final class TestConfig {

    private TestConfig() {}

    public static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    /* Directly call services for seeding (bypasses gateway JWT) */
    public static final String USERS_DIRECT = System.getProperty("usersUrl", "http://localhost:8081");
    public static final String THEATERS_DIRECT = System.getProperty("theatersUrl", "http://localhost:8082");
    public static final String MOVIES_DIRECT = System.getProperty("moviesUrl", "http://localhost:8083");
    public static final String SEATS_DIRECT = System.getProperty("seatsUrl", "http://localhost:8084");

    public static HttpProtocolBuilder httpProtocol() {
        return http
                .baseUrl(BASE_URL)
                .acceptHeader("application/json")
                .contentTypeHeader("application/json")
                .userAgentHeader("Gatling-LoadTest/1.0");
    }

    /**
     * Build HTTP protocol for direct service calls (seeding, bypasses gateway).
     */
    public static HttpProtocolBuilder directProtocol(String baseUrl) {
        return http
                .baseUrl(baseUrl)
                .acceptHeader("application/json")
                .contentTypeHeader("application/json");
    }
}
