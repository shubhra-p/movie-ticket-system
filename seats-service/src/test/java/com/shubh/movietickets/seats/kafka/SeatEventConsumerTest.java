package com.shubh.movietickets.seats.kafka;

import com.shubh.movietickets.common.event.BookingConfirmedEvent;
import com.shubh.movietickets.common.event.BookingFailedEvent;
import com.shubh.movietickets.common.event.ScheduleCreatedEvent;
import com.shubh.movietickets.seats.service.SeatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatEventConsumerTest {

    @Mock
    private SeatService seatService;

    @InjectMocks
    private SeatEventConsumer consumer;

    @Test
    @DisplayName("onScheduleCreated — seeds seats from event's seat layout info")
    @SuppressWarnings("unchecked")
    void onScheduleCreated_seedsSeats() {
        UUID scheduleId = UUID.randomUUID();
        UUID screenId = UUID.randomUUID();

        ScheduleCreatedEvent.SeatLayoutInfo regularLayout =
                ScheduleCreatedEvent.SeatLayoutInfo.builder()
                        .category("REGULAR")
                        .rowPrefix("A")
                        .rowCount(5)
                        .seatsPerRow(10)
                        .price(BigDecimal.valueOf(200))
                        .build();

        ScheduleCreatedEvent.SeatLayoutInfo premiumLayout =
                ScheduleCreatedEvent.SeatLayoutInfo.builder()
                        .category("PREMIUM")
                        .rowPrefix("F")
                        .rowCount(2)
                        .seatsPerRow(8)
                        .price(BigDecimal.valueOf(400))
                        .build();

        ScheduleCreatedEvent event = ScheduleCreatedEvent.builder()
                .sourceService("movies-service")
                .scheduleId(scheduleId)
                .screenId(screenId)
                .showDate(LocalDate.now().plusDays(1))
                .showTime(LocalTime.of(18, 30))
                .seatLayouts(List.of(regularLayout, premiumLayout))
                .build();

        consumer.onScheduleCreated(event);

        ArgumentCaptor<List<SeatService.SeatSeedInfo>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(seatService).seedSeats(eq(scheduleId), captor.capture());

        List<SeatService.SeatSeedInfo> layouts = captor.getValue();
        assertThat(layouts).hasSize(2);
        assertThat(layouts.get(0).category()).isEqualTo("REGULAR");
        assertThat(layouts.get(0).rowPrefix()).isEqualTo("A");
        assertThat(layouts.get(0).rowCount()).isEqualTo(5);
        assertThat(layouts.get(0).seatsPerRow()).isEqualTo(10);
        assertThat(layouts.get(0).price()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(layouts.get(1).category()).isEqualTo("PREMIUM");
        assertThat(layouts.get(1).rowPrefix()).isEqualTo("F");
    }

    @Test
    @DisplayName("onBookingConfirmed — confirms seats via SeatService")
    void onBookingConfirmed_confirmSeats() {
        UUID bookingId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<String> seatNumbers = List.of("A1", "A2", "A3");

        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .sourceService("tickets-service")
                .bookingId(bookingId)
                .scheduleId(scheduleId)
                .userId(userId)
                .seatNumbers(seatNumbers)
                .totalAmount(BigDecimal.valueOf(600))
                .build();

        consumer.onBookingConfirmed(event);

        verify(seatService).confirmSeats(scheduleId, seatNumbers, userId);
    }

    @Test
    @DisplayName("onBookingFailed — releases seats via SeatService")
    void onBookingFailed_releasesSeats() {
        UUID bookingId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<String> seatNumbers = List.of("B1", "B2");

        BookingFailedEvent event = BookingFailedEvent.builder()
                .sourceService("tickets-service")
                .bookingId(bookingId)
                .scheduleId(scheduleId)
                .userId(userId)
                .seatNumbers(seatNumbers)
                .reason("Payment declined")
                .build();

        consumer.onBookingFailed(event);

        verify(seatService).releaseSeats(scheduleId, seatNumbers, userId);
    }
}
