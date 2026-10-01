package com.shubh.movietickets.tickets.kafka;

import com.shubh.movietickets.common.event.BookingConfirmedEvent;
import com.shubh.movietickets.common.event.BookingFailedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private BookingEventProducer producer;

    @Test
    @DisplayName("publishBookingConfirmed — sends to 'booking-confirmed' topic with bookingId as key")
    void publishBookingConfirmed_correctTopicAndKey() {
        UUID bookingId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .sourceService("tickets-service")
                .bookingId(bookingId)
                .scheduleId(scheduleId)
                .userId(userId)
                .seatNumbers(List.of("A1", "A2"))
                .totalAmount(BigDecimal.valueOf(400))
                .build();

        producer.publishBookingConfirmed(event);

        verify(kafkaTemplate).send(
                eq("booking-confirmed"),
                eq(bookingId.toString()),
                eq(event)
        );
    }

    @Test
    @DisplayName("publishBookingFailed — sends to 'booking-failed' topic with bookingId as key")
    void publishBookingFailed_correctTopicAndKey() {
        UUID bookingId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        BookingFailedEvent event = BookingFailedEvent.builder()
                .sourceService("tickets-service")
                .bookingId(bookingId)
                .scheduleId(scheduleId)
                .userId(userId)
                .seatNumbers(List.of("B1"))
                .reason("Payment declined")
                .build();

        producer.publishBookingFailed(event);

        verify(kafkaTemplate).send(
                eq("booking-failed"),
                eq(bookingId.toString()),
                eq(event)
        );
    }

    @Test
    @DisplayName("publishBookingConfirmed — event payload contains correct data")
    void publishBookingConfirmed_eventPayloadIntegrity() {
        UUID bookingId = UUID.randomUUID();

        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .sourceService("tickets-service")
                .bookingId(bookingId)
                .scheduleId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .seatNumbers(List.of("C1", "C2", "C3"))
                .totalAmount(BigDecimal.valueOf(600))
                .build();

        producer.publishBookingConfirmed(event);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("booking-confirmed"), eq(bookingId.toString()), eventCaptor.capture());

        BookingConfirmedEvent captured = (BookingConfirmedEvent) eventCaptor.getValue();
        assertThat(captured.getBookingId()).isEqualTo(bookingId);
        assertThat(captured.getSeatNumbers()).containsExactly("C1", "C2", "C3");
        assertThat(captured.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(captured.getSourceService()).isEqualTo("tickets-service");
    }

    @Test
    @DisplayName("publishBookingFailed — event payload contains failure reason")
    void publishBookingFailed_eventPayloadIntegrity() {
        UUID bookingId = UUID.randomUUID();

        BookingFailedEvent event = BookingFailedEvent.builder()
                .sourceService("tickets-service")
                .bookingId(bookingId)
                .scheduleId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .seatNumbers(List.of("D1"))
                .reason("Insufficient funds")
                .build();

        producer.publishBookingFailed(event);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("booking-failed"), eq(bookingId.toString()), eventCaptor.capture());

        BookingFailedEvent captured = (BookingFailedEvent) eventCaptor.getValue();
        assertThat(captured.getReason()).isEqualTo("Insufficient funds");
    }
}
