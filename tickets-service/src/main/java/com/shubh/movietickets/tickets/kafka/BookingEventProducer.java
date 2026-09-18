package com.shubh.movietickets.tickets.kafka;

import com.shubh.movietickets.common.event.BookingConfirmedEvent;
import com.shubh.movietickets.common.event.BookingFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Publishing BookingConfirmedEvent: bookingId={}, scheduleId={}",
                event.getBookingId(), event.getScheduleId());
        kafkaTemplate.send("booking-confirmed", event.getBookingId().toString(), event);
    }

    public void publishBookingFailed(BookingFailedEvent event) {
        log.info("Publishing BookingFailedEvent: bookingId={}, reason={}",
                event.getBookingId(), event.getReason());
        kafkaTemplate.send("booking-failed", event.getBookingId().toString(), event);
    }
}
