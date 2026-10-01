package com.shubh.movietickets.payment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/* Admin endpoint to control mock behavior for testing scenarios */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MockConfig {

    /* Percentage of payments that fail (0-100). Default 0. */
    @Min(0) @Max(100)
    private int failureRate = 0;

    /* Simulated processing delay in milliseconds. Default 1000ms. */
    @Min(0) @Max(30000)
    private long processingDelayMs = 1000;

    /* If true, the next payment will always fail (one-shot). */
    private boolean forceNextFailure = false;
}
