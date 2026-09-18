package com.shubh.movietickets.seats.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/*
 Individual seat for a specific schedule.
 Seeded by Kafka consumer when ScheduleCreatedEvent arrives.
 Uses @Version for optimistic locking, preventing lost updates
 when concurrent requests try to lock the same seat.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "seats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"schedule_id", "seat_number"})
})
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    private String category;

    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;

    private UUID lockedByUserId;

    @Version
    private Long version;
}
