package com.shubh.movietickets.seats.kafka;

import com.shubh.movietickets.common.event.BookingConfirmedEvent;
import com.shubh.movietickets.common.event.BookingFailedEvent;
import com.shubh.movietickets.common.event.ScheduleCreatedEvent;
import com.shubh.movietickets.seats.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeatEventConsumer {

    private final SeatService seatService;

    @KafkaListener(topics = "schedule-created", groupId = "seats-service")
    public void onScheduleCreated(ScheduleCreatedEvent event) {
        log.info("Received ScheduleCreatedEvent: scheduleId={}, screenId={}",
                event.getScheduleId(), event.getScreenId());

        List<SeatService.SeatSeedInfo> layouts = event.getSeatLayouts().stream()
                .map(sl -> new SeatService.SeatSeedInfo(
                        sl.getCategory(), sl.getRowPrefix(),
                        sl.getRowCount(), sl.getSeatsPerRow(), sl.getPrice()))
                .toList();

        seatService.seedSeats(event.getScheduleId(), layouts);
    }

    @KafkaListener(topics = "booking-confirmed", groupId = "seats-service")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Received BookingConfirmedEvent: bookingId={}, scheduleId={}",
                event.getBookingId(), event.getScheduleId());

        seatService.confirmSeats(
                event.getScheduleId(),
                event.getSeatNumbers(),
                event.getUserId());
    }

    @KafkaListener(topics = "booking-failed", groupId = "seats-service")
    public void onBookingFailed(BookingFailedEvent event) {
        log.info("Received BookingFailedEvent: bookingId={}, reason={}",
                event.getBookingId(), event.getReason());

        seatService.releaseSeats(
                event.getScheduleId(),
                event.getSeatNumbers(),
                event.getUserId());
    }
}
