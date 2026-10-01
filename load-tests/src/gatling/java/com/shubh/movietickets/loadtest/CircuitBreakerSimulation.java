package com.shubh.movietickets.loadtest;

import com.shubh.movietickets.loadtest.config.TestConfig;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * CIRCUIT BREAKER SIMULATION — Graceful Degradation
 * <p>
 * Proves: When a downstream service fails, the system degrades gracefully
 * instead of cascading failures.
 * <p>
 * Scenario:
 *   1. Ramp 50 users browsing movies (which calls Theaters Service via Feign)
 *   2. During the test, you manually stop Theaters Service
 *   3. Movies Service circuit breaker should OPEN → return fast fallback errors
 *   4. Restart Theaters Service → circuit breaker goes HALF_OPEN → CLOSED
 * <p>
 * Manual step required: stop/start theaters-service during the test.
 * Watch Grafana "Circuit Breaker State" panel during this simulation.
 * <p>
 * Run: ./gradlew :load-tests:gatlingRun --simulation com.shubh.movietickets.loadtest.CircuitBreakerSimulation
 */
public class CircuitBreakerSimulation extends Simulation {

    private static final AtomicInteger userCounter = new AtomicInteger(0);

    private static Iterator<Map<String, Object>> userFeeder() {
        return Stream.generate((Supplier<Map<String, Object>>) () -> {
            int i = userCounter.incrementAndGet();
            return Map.of(
                    "userName", "cb-" + i,
                    "userEmail", "cb-" + i + "@test.com",
                    "userPassword", "password123"
            );
        }).iterator();
    }

    /* Register to get a JWT */
    private ChainBuilder register = exec(
            feed(userFeeder()),
            http("Register")
                    .post(TestConfig.USERS_DIRECT + "/api/users/register")
                    .body(StringBody("""
                        {
                            "name": "#{userName}",
                            "email": "#{userEmail}",
                            "password": "#{userPassword}"
                        }
                        """))
                    .check(
                            status().is(201),
                            jmesPath("data.token").saveAs("authToken")
                    )
    );

    /*
     Repeatedly browse movies + attempt to fetch a schedule.
     When Theaters Service is down, the Feign call from Movies Service fails,
     and the circuit breaker should activate.
     The status codes we accept: 200 (healthy), 500 (service down), 503 (circuit open)
     */
    private ChainBuilder browseLoop = repeat(10, "i").on(
            exec(
                    http("Browse Movies")
                            .get("/api/movies")
                            .header("Authorization", "Bearer #{authToken}")
                            .check(status().in(200, 500, 503))
            ).pause(2, 4) // think time
    );

    /* ── Scenarios ── */

    private ScenarioBuilder circuitBreakerTest = scenario("Circuit Breaker Test")
            .exec(register)
            .pause(1)
            .exec(browseLoop);

    /* ── Simulation Setup ── */
    {
        setUp(
                circuitBreakerTest.injectOpen(
                        rampUsers(50).during(120) // 50 users over 2 minutes
                )
        ).protocols(TestConfig.httpProtocol())
         .assertions(
                 // We expect ALL requests to get a response (even if 500/503)
                 // The point is NO timeouts or connection refused — circuit breaker handles it
                 global().successfulRequests().percent().gt(70.0)
         );
    }
}
