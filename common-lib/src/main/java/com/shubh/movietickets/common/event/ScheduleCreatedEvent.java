package com.shubh.movietickets.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/*
 Published by Movies Service when a new show schedule is created.
 Consumed by Seats Service to seed seat availability rows.
 Carries the full seat layout so Seats Service can generate
 individual seat records without calling Theaters Service.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScheduleCreatedEvent extends BaseEvent {
    private UUID scheduleId;
    private UUID screenId;
    private LocalDate showDate;
    private LocalTime showTime;
    private List<SeatLayoutInfo> seatLayouts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatLayoutInfo {
        private String category;
        private String rowPrefix;
        private int rowCount;
        private int seatsPerRow;
        private BigDecimal price;
    }
}
