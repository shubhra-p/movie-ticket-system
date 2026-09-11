package com.shubh.movietickets.theaters.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/*
 Defines a section of seats within a screen.
 e.g., Row A-E = REGULAR (10 seats/row), Row F-G = PREMIUM (8 seats/row)
 The Seats Service uses this to generate individual seat records per schedule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "seat_layouts")
public class SeatLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    @JsonIgnore
    private Screen screen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatCategory category;

    @Column(nullable = false)
    private String rowPrefix;

    @Column(nullable = false)
    private int rowCount;

    @Column(nullable = false)
    private int seatsPerRow;

    @Column(nullable = false)
    private BigDecimal price;
}
