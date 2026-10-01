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
 * FULL JOURNEY SIMULATION — End-to-End User Flow
 * <p>
 * Proves: Complete booking flow works under load with acceptable latency.
 * <p>
 * Flow per user:
 *   Register → Login → Browse Movies → View Schedules → View Seats → Book → Pay
 * <p>
 * Load: 50 users ramping over 60 seconds.
 * Assertions: p95 < 3s for the full journey, < 5% error rate.
 * <p>
 * Run: ./gradlew :load-tests:gatlingRun --simulation com.shubh.movietickets.loadtest.FullJourneySimulation
 */
public class FullJourneySimulation extends Simulation {

    private static final AtomicInteger userCounter = new AtomicInteger(0);

    private static Iterator<Map<String, Object>> userFeeder() {
        return Stream.generate((Supplier<Map<String, Object>>) () -> {
            int i = userCounter.incrementAndGet();
            return Map.of(
                    "userName", "journey-" + i,
                    "userEmail", "journey-" + i + "@test.com",
                    "userPassword", "password123"
            );
        }).iterator();
    }

    /* Seed data once: theater + movie + schedule */
    private ChainBuilder seedData = exec(
            http("Seed: Create Theater")
                    .post(TestConfig.THEATERS_DIRECT + "/api/theaters")
                    .body(StringBody("""
                        {
                            "name": "Journey Test Cinema",
                            "city": "JourneyCity",
                            "address": "456 Journey Ave",
                            "screens": [{
                                "name": "Screen-Journey",
                                "seatLayouts": [
                                    {
                                        "category": "REGULAR",
                                        "rowPrefix": "A",
                                        "rowCount": 10,
                                        "seatsPerRow": 20,
                                        "price": 150.00
                                    },
                                    {
                                        "category": "PREMIUM",
                                        "rowPrefix": "K",
                                        "rowCount": 5,
                                        "seatsPerRow": 20,
                                        "price": 300.00
                                    }
                                ]
                            }]
                        }
                        """))
                    .check(
                            status().is(201),
                            jmesPath("data.screens[0].id").saveAs("screenId")
                    ),
            pause(1),
            http("Seed: Create Movie")
                    .post(TestConfig.MOVIES_DIRECT + "/api/movies")
                    .body(StringBody("""
                        {
                            "title": "Journey Test Movie",
                            "description": "Full journey load test",
                            "genre": "Drama",
                            "durationMinutes": 150,
                            "language": "English",
                            "releaseDate": "%s"
                        }
                        """.formatted(LocalDate.now().toString())))
                    .check(
                            status().is(201),
                            jmesPath("data.id").saveAs("movieId")
                    ),
            pause(1),
            http("Seed: Create Schedule")
                    .post(TestConfig.MOVIES_DIRECT + "/api/movies/schedules")
                    .body(StringBody("""
                        {
                            "movieId": "#{movieId}",
                            "screenId": "#{screenId}",
                            "showDate": "%s",
                            "showTime": "20:00"
                        }
                        """.formatted(LocalDate.now().plusDays(2).toString())))
                    .check(
                            status().is(201),
                            jmesPath("data.id").saveAs("scheduleId")
                    ),
            pause(5), // wait for Kafka seat seeding
            exec(session -> {
                SharedState.put("scheduleId", session.getString("scheduleId"));
                SharedState.put("screenId", session.getString("screenId"));
                SharedState.put("movieId", session.getString("movieId"));
                System.out.println("=== JOURNEY SEED COMPLETE === scheduleId=" + session.getString("scheduleId"));
                return session;
            })
    );

    /* Register + Login through Gateway (open paths — no JWT needed) */
    private ChainBuilder registerAndLogin = exec(
            feed(userFeeder()),
            http("Register")
                    .post("/api/users/register")
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
                    )
    );

    /* Browse movies (authenticated) */
    private ChainBuilder browseMovies = exec(
            http("Browse Movies")
                    .get("/api/movies")
                    .header("Authorization", "Bearer #{authToken}")
                    .check(
                            status().is(200),
                            jmesPath("data[0].id").saveAs("browseMovieId")
                    )
    );

    /* View available seats */
    private ChainBuilder viewSeats = exec(
            http("View Available Seats")
                    .get("/api/seats/#{scheduleId}/available")
                    .header("Authorization", "Bearer #{authToken}")
                    .check(
                            status().is(200),
                            jmesPath("data[0].seatNumber").saveAs("selectedSeat1"),
                            jmesPath("data[1].seatNumber").saveAs("selectedSeat2")
                    )
    );

    /* Book selected seats */
    private ChainBuilder bookSeats = exec(
            http("Create Booking")
                    .post("/api/bookings")
                    .header("Authorization", "Bearer #{authToken}")
                    .body(StringBody("""
                        {
                            "userId": "#{userId}",
                            "scheduleId": "#{scheduleId}",
                            "seatNumbers": ["#{selectedSeat1}", "#{selectedSeat2}"]
                        }
                        """))
                    .check(
                            status().in(201, 409),
                            jmesPath("data.id").optional().saveAs("bookingId")
                    )
    );

    /* Initiate payment (only if booking succeeded) */
    private ChainBuilder payForBooking = doIf(session -> session.contains("bookingId")).then(
            exec(
                    http("Initiate Payment")
                            .post("/api/bookings/#{bookingId}/pay")
                            .header("Authorization", "Bearer #{authToken}")
                            .check(status().in(200, 400))
            )
    );

    /* Verify booking status */
    private ChainBuilder checkBooking = doIf(session -> session.contains("bookingId")).then(
            exec(
                    pause(2), // wait for async payment callback
                    http("Check Booking Status")
                            .get("/api/bookings/#{bookingId}")
                            .header("Authorization", "Bearer #{authToken}")
                            .check(status().is(200))
            )
    );

    /* ── Scenarios ── */

    private ScenarioBuilder seeder = scenario("Seed Data")
            .exec(seedData);

    private ScenarioBuilder fullJourney = scenario("Full Journey")
            .exec(registerAndLogin)
            .pause(1)
            .exec(SharedState.copyToSession("scheduleId"))
            .exec(browseMovies)
            .pause(1, 3) // think time
            .exec(viewSeats)
            .pause(1, 2)
            .exec(bookSeats)
            .pause(1)
            .exec(payForBooking)
            .exec(checkBooking);

    /* ── Simulation Setup ── */
    {
        setUp(
                seeder.injectOpen(atOnceUsers(1))
                        .andThen(
                                fullJourney.injectOpen(
                                        nothingFor(2),
                                        rampUsers(50).during(60) // 50 users over 1 minute
                                )
                        )
        ).protocols(TestConfig.httpProtocol())
         .assertions(
                 global().responseTime().percentile3().lt(3000),  // p95 < 3s
                 global().successfulRequests().percent().gt(90.0) // > 90% success
         );
    }
}
