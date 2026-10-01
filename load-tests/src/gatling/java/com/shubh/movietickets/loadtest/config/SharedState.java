package com.shubh.movietickets.loadtest.config;

import io.gatling.javaapi.core.Session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Thread-safe shared state for passing data between Gatling scenarios.
 * <p>
 * Gatling scenarios run independently with isolated sessions. This class provides
 * a static bridge for the seeder scenario to publish IDs (scheduleId, movieId, etc.)
 * that load test scenarios need to reference.
 * <p>
 * Usage:
 * <pre>
 *   // In seeder scenario (after creating resources):
 *   exec(session -> {
 *       SharedState.put("scheduleId", session.getString("scheduleId"));
 *       return session;
 *   })
 *
 *   // In load test scenario (before making requests):
 *   exec(SharedState.copyToSession("scheduleId", "movieId"))
 * </pre>
 */
public final class SharedState {

    private SharedState() {}

    private static final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    /** Store a key-value pair in the shared state. */
    public static void put(String key, String value) {
        if (value != null) {
            store.put(key, value);
        }
    }

    /** Retrieve a value from the shared state. */
    public static String get(String key) {
        return store.get(key);
    }

    /**
     * Returns a Gatling session function that copies the specified keys
     * from SharedState into the virtual user's session.
     * <p>
     * Use this in an exec() block to inject shared data into a scenario:
     * <pre>exec(SharedState.copyToSession("scheduleId", "screenId"))</pre>
     */
    public static Function<Session, Session> copyToSession(String... keys) {
        return session -> {
            Session updated = session;
            for (String key : keys) {
                String value = store.get(key);
                if (value != null) {
                    updated = updated.set(key, value);
                }
            }
            return updated;
        };
    }

    /** Clear all shared state (useful between simulation runs in the same JVM). */
    public static void clear() {
        store.clear();
    }
}
