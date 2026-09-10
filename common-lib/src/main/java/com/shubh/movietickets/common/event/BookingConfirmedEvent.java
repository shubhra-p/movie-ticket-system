package com.shubh.movietickets.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

/*
 Published by Tickets Service when a booking is confirmed after successful payment.
 Consumed by Seats Service to flip seat status from LOCKED to BOOKED in Postgres
 and delete the Redis lock.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BookingConfirmedEvent extends BaseEvent {
    private Long bookingId;
    private Long scheduleId;
    private String userId;
    private List<String> seatNumbers;
    private BigDecimal totalAmount;
}
