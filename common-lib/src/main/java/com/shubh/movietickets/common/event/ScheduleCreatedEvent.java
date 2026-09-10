package com.shubh.movietickets.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/*
 Published by Movies Service when a new show schedule is created.
 Consumed by Seats Service to seed seat availability rows.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScheduleCreatedEvent extends BaseEvent {
    private Long scheduleId;
    private Long movieId;
    private Long theaterId;
    private Long screenId;
    private LocalDate showDate;
    private LocalTime showTime;
    private BigDecimal basePrice;
}
