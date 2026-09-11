package com.shubh.movietickets.movies.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/*
 A specific show, links a Movie to a Screen at a date + time.
 When created, publishes ScheduleCreatedEvent via Kafka
 so the Seats Service can seed individual seat availability rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movie_schedules", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"screen_id", "show_date", "show_time"})
})
@EntityListeners(AuditingEntityListener.class)
public class MovieSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "screen_id", nullable = false)
    private UUID screenId;

    @Column(name = "theater_id", nullable = false)
    private UUID theaterId;

    @Column(nullable = false)
    private String theaterName;

    @Column(nullable = false)
    private String screenName;

    @Column(name = "show_date", nullable = false)
    private LocalDate showDate;

    @Column(name = "show_time", nullable = false)
    private LocalTime showTime;

    @Column(nullable = false)
    private int totalSeats;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;
}
