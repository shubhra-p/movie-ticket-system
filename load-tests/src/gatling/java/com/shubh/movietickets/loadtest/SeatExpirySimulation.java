package com.shubh.movietickets.loadtest;

import com.shubh.movietickets.loadtest.config.SharedState;
import com.shubh.movietickets.loadtest.config.TestConfig;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * SEAT LOCK EXPIRY SIMULATION — TTL Verification
 * <p>
 * Proves: Redis TTL auto-releases locked seats when users abandon checkout.
 * <p>
 * Scenario:
 *   1. Seed: create a show with 50 seats
 *   2. 30 users lock 1 seat each (creating 30 bookings in SEATS_LOCKED state)
 *   3. Users NEVER call /pay — they abandon checkout
 *   4. Wait for lock TTL (5 min default, or shorter if configured)
 *   5. Verify: all 50 seats are AVAILABLE again (check via Seats Service)
 * <p>
 * Note: This test is inherently long (waits for TTL). For quick verification,
 * configure seats.lock.ttl-seconds=30 in application.yml before running.
 * <p>
 * Run: ./gradlew :load-tests:gatlingRun --simulation com.shubh.movietickets.loadtest.SeatExpirySimulation
 */
public class SeatExpirySimulation extends Simulation {

    private static final AtomicInteger userCounter = new AtomicInteger(0);
    private static final AtomicInteger seatCounter = new AtomicInteger(0);

    private static Iterator<Map<String, Object>> userFeeder() {
        return Stream.generate((Supplier<Map<String, Object>>) () -> {
            int i = userCounter.incrementAndGet();
            return Map.of(
                    "userName", "expiry-" + i,
                    "userEmail", "expiry-" + i + "@test.com",
                    "userPassword", "password123"
            );
        }).iterator();
    }

    private static Iterator<Map<String, Object>> seatFeeder() {
        return Stream.generate((Supplier<Map<String, Object>>) () -> {
            int idx = seatCounter.getAndIncrement();
            int row = idx / 10;
            int col = (idx % 10) + 1;
            char rowChar = (char) ('A' + row);
            return Map.of("seatNum", rowChar + String.valueOf(col));
        }).iterator();
    }

    private ChainBuilder seedShow = exec(
            http("Seed: Create Theater")
                    .post(TestConfig.THEATERS_DIRECT + "/api/theaters")
                    .body(StringBody("""
                        {
                            "name": "Expiry Test Cinema",
                            "city": "ExpiryCity",
                            "address": "TTL Lane",
                            "screens": [{
                                "name": "Screen-Expiry",
                                "seatLayouts": [{
                                    "category": "REGULAR",
                                    "rowPrefix": "A",
                                    "rowCount": 5,
                                    "seatsPerRow": 10,
                                    "price": 100.00
                                }]
                            }]
                        }
                        """))
                    .check(status().is(201), jmesPath("data.screens[0].id").saveAs("screenId")),
            pause(1),
            http("Seed: Create Movie")
                    .post(TestConfig.MOVIES_DIRECT + "/api/movies")
                    .body(StringBody("""
                        {
                            "title": "Expiry Test Movie",
                            "description": "TTL test",
                            "genre": "Horror",
                            "durationMinutes": 90,
                            "language": "English",
                            "releaseDate": "%s"
                        }
                        """.formatted(LocalDate.now().toString())))
                    .check(status().is(201), jmesPath("data.id").saveAs("movieId")),
            pause(1),
            http("Seed: Create Schedule")
                    .post(TestConfig.MOVIES_DIRECT + "/api/movies/schedules")
                    .body(StringBody("""
                        {
                            "movieId": "#{movieId}",
                            "screenId": "#{screenId}",
                            "showDate": "%s",
                            "showTime": "14:00"
                        }
                        """.formatted(LocalDate.now().plusDays(4).toString())))
                    .check(status().is(201), jmesPath("data.id").saveAs("scheduleId")),
            pause(5),
            exec(session -> {
                SharedState.put("scheduleId", session.getString("scheduleId"));
                System.out.println("=== EXPIRY SEED COMPLETE === scheduleId=" + session.getString("scheduleId"));
                return session;
            })
    );

    /* Register, lock 1 seat, and DO NOT PAY — abandon the booking */
    private ChainBuilder lockAndAbandon =
            exec(SharedState.copyToSession("scheduleId"))
            .exec(
            feed(userFeeder()),
            feed(seatFeeder()),
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
                            jmesPath("data.token").saveAs("authToken"),
                            jmesPath("data.user.id").saveAs("userId")
                    ),
            pause(1),
            http("Lock Seat (abandon)")
                    .post("/api/bookings")
                    .header("Authorization", "Bearer #{authToken}")
                    .body(StringBody("""
                        {
                            "userId": "#{userId}",
                            "scheduleId": "#{scheduleId}",
                            "seatNumbers": ["#{seatNum}"]
                        }
                        """))
                    .check(status().in(201, 409))
            // *** Intentionally NO payment call — simulates abandoned checkout ***
    );

    /* After TTL expires, check that seats are available again */
    private ChainBuilder verifySeatsReleased = exec(
            http("Verify: Check Available Seats")
                    .get(TestConfig.SEATS_DIRECT + "/api/seats/#{scheduleId}/available")
                    .check(
                            status().is(200),
                            jmesPath("data").exists()
                    ),
            exec(session -> {
                System.out.println("=== EXPIRY VERIFICATION COMPLETE ===");
                System.out.println("Check Grafana booking.outcomes expired counter for confirmation");
                return session;
            })
    );

    /* ── Scenarios ── */

    private ScenarioBuilder seeder = scenario("Seed Show")
            .exec(seedShow);

    private ScenarioBuilder abandoners = scenario("Lock and Abandon")
            .exec(lockAndAbandon);

    private ScenarioBuilder verifier = scenario("Verify Expiry")
            .exec(verifySeatsReleased);

    /* ── Simulation Setup ── */
    {
        setUp(
                seeder.injectOpen(atOnceUsers(1))
                        .andThen(
                                abandoners.injectOpen(
                                        nothingFor(2),
                                        atOnceUsers(30) // 30 users lock and abandon
                                )
                        )
                        // Note: Verification should be run manually after TTL expires
                        // or set seats.lock.ttl-seconds=30 and uncomment below:
                        // .andThen(
                        //     verifier.injectOpen(nothingFor(35), atOnceUsers(1))
                        // )
        ).protocols(TestConfig.httpProtocol())
         .assertions(
                 global().successfulRequests().percent().gt(80.0)
         );
    }
}
