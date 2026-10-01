package com.shubh.movietickets.loadtest;

import com.shubh.movietickets.loadtest.config.SharedState;
import com.shubh.movietickets.loadtest.config.TestConfig;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * SUSTAINED LOAD SIMULATION — Throughput Under Pressure
 * <p>
 * Proves: System handles sustained booking throughput without degradation.
 * <p>
 * Scenario:
 *   - Seed 5 different shows (schedules) with 200 seats each (1000 total seats)
 *   - 100 concurrent users continuously book across all 5 shows over 3 minutes
 *   - Each user picks a random show and books 1-2 seats
 * <p>
 * Assertions: p95 < 1s, error rate < 5%
 * <p>
 * Run: ./gradlew :load-tests:gatlingRun --simulation com.shubh.movietickets.loadtest.SustainedLoadSimulation
 */
public class SustainedLoadSimulation extends Simulation {

    private static final AtomicInteger userCounter = new AtomicInteger(0);
    private static final AtomicInteger showCounter = new AtomicInteger(0);

    private static Iterator<Map<String, Object>> userFeeder() {
        return Stream.generate((Supplier<Map<String, Object>>) () -> {
            int i = userCounter.incrementAndGet();
            return Map.of(
                    "userName", "sustained-" + i,
                    "userEmail", "sustained-" + i + "@test.com",
                    "userPassword", "password123"
            );
        }).iterator();
    }

    /* Generates a seat pair from a large pool to reduce contention across shows */
    private static final AtomicInteger seatPool = new AtomicInteger(0);

    private static Iterator<Map<String, Object>> seatFeeder() {
        return Stream.generate((Supplier<Map<String, Object>>) () -> {
            int idx = seatPool.getAndIncrement();
            int row = (idx % 20);  // 20 rows A-T
            int col = (idx / 20) % 10 + 1;
            char rowChar = (char) ('A' + row);
            return Map.of(
                    "seatNum", rowChar + String.valueOf(col)
            );
        }).iterator();
    }

    /* Seed: create 1 theater with 5 screens, 1 movie, 5 schedules */
    private ChainBuilder seedMultipleShows = exec(
            http("Seed: Create Theater (5 screens)")
                    .post(TestConfig.THEATERS_DIRECT + "/api/theaters")
                    .body(StringBody("""
                        {
                            "name": "Sustained Load Cinema",
                            "city": "LoadCity",
                            "address": "789 Throughput Blvd",
                            "screens": [
                                {"name": "Screen-S1", "seatLayouts": [{"category": "REGULAR", "rowPrefix": "A", "rowCount": 20, "seatsPerRow": 10, "price": 150.00}]},
                                {"name": "Screen-S2", "seatLayouts": [{"category": "REGULAR", "rowPrefix": "A", "rowCount": 20, "seatsPerRow": 10, "price": 150.00}]},
                                {"name": "Screen-S3", "seatLayouts": [{"category": "REGULAR", "rowPrefix": "A", "rowCount": 20, "seatsPerRow": 10, "price": 150.00}]},
                                {"name": "Screen-S4", "seatLayouts": [{"category": "REGULAR", "rowPrefix": "A", "rowCount": 20, "seatsPerRow": 10, "price": 150.00}]},
                                {"name": "Screen-S5", "seatLayouts": [{"category": "REGULAR", "rowPrefix": "A", "rowCount": 20, "seatsPerRow": 10, "price": 150.00}]}
                            ]
                        }
                        """))
                    .check(
                            status().is(201),
                            jmesPath("data.screens[0].id").saveAs("screen1"),
                            jmesPath("data.screens[1].id").saveAs("screen2"),
                            jmesPath("data.screens[2].id").saveAs("screen3"),
                            jmesPath("data.screens[3].id").saveAs("screen4"),
                            jmesPath("data.screens[4].id").saveAs("screen5")
                    ),
            pause(1),
            http("Seed: Create Movie")
                    .post(TestConfig.MOVIES_DIRECT + "/api/movies")
                    .body(StringBody("""
                        {
                            "title": "Sustained Load Movie",
                            "description": "Throughput test",
                            "genre": "Thriller",
                            "durationMinutes": 120,
                            "language": "English",
                            "releaseDate": "%s"
                        }
                        """.formatted(LocalDate.now().toString())))
                    .check(status().is(201), jmesPath("data.id").saveAs("movieId")),
            pause(1),
            /* Create 5 schedules, one per screen */
            exec(createSchedule("1", "10:00")),
            exec(createSchedule("2", "13:00")),
            exec(createSchedule("3", "16:00")),
            exec(createSchedule("4", "19:00")),
            exec(createSchedule("5", "22:00")),
            pause(8), // extra time for Kafka to seed seats for all 5 schedules
            exec(session -> {
                System.out.println("=== SUSTAINED LOAD: 5 schedules seeded ===");
                for (int i = 1; i <= 5; i++) {
                    String key = "schedule" + i;
                    SharedState.put(key, session.getString(key));
                    System.out.println("Schedule " + i + ": " + session.getString(key));
                }
                return session;
            })
    );

    private static ChainBuilder createSchedule(String num, String time) {
        String showDate = LocalDate.now().plusDays(3).toString();
        return exec(
                http("Seed: Create Schedule " + num)
                        .post(TestConfig.MOVIES_DIRECT + "/api/movies/schedules")
                        .body(StringBody("""
                            {
                                "movieId": "#{movieId}",
                                "screenId": "#{screen%s}",
                                "showDate": "%s",
                                "showTime": "%s"
                            }
                            """.formatted(num, showDate, time)))
                        .check(status().is(201), jmesPath("data.id").saveAs("schedule" + num)),
                pause(1)
        );
    }

    /* Register and get JWT */
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
                            jmesPath("data.token").saveAs("authToken"),
                            jmesPath("data.user.id").saveAs("userId")
                    )
    );

    /* Pick a random show and book 1 seat */
    private ChainBuilder bookRandomShow = exec(
            feed(seatFeeder()),
            exec(session -> {
                // Pick a random schedule (1-5)
                int showNum = (int) (Math.random() * 5) + 1;
                String scheduleId = session.getString("schedule" + showNum);
                return session.set("scheduleId", scheduleId);
            }),
            http("Book Seat")
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
    );

    /* ── Scenarios ── */

    private ScenarioBuilder seeder = scenario("Seed Shows")
            .exec(seedMultipleShows);

    private ScenarioBuilder sustainedBookers = scenario("Sustained Booking")
            .exec(register)
            .pause(1)
            .exec(SharedState.copyToSession("schedule1", "schedule2", "schedule3", "schedule4", "schedule5"))
            .repeat(3).on(  // each user books 3 times
                    exec(bookRandomShow).pause(2, 5) // think time between bookings
            );

    /* ── Simulation Setup ── */
    {
        setUp(
                seeder.injectOpen(atOnceUsers(1))
                        .andThen(
                                sustainedBookers.injectOpen(
                                        nothingFor(3),
                                        rampUsers(100).during(180) // 100 users over 3 minutes
                                )
                        )
        ).protocols(TestConfig.httpProtocol())
         .assertions(
                 global().responseTime().percentile3().lt(1000), // p95 < 1s
                 global().successfulRequests().percent().gt(95.0)
         );
    }
}
