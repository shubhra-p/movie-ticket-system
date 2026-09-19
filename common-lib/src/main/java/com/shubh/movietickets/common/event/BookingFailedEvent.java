package com.shubh.movietickets.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

/*
 Published by Tickets Service when a booking fails (payment declined, expired, etc.).
 Consumed by Seats Service to release the Redis lock and revert Postgres status.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BookingFailedEvent extends BaseEvent {
    private UUID bookingId;
    private UUID scheduleId;
    private UUID userId;
    private List<String> seatNumbers;
    private String reason;
}
