package com.shubh.movietickets.movies.kafka;

import com.shubh.movietickets.common.event.ScheduleCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleEventProducer {

    private static final String TOPIC = "schedule-created";

    private final KafkaTemplate<String, ScheduleCreatedEvent> kafkaTemplate;

    public void publishScheduleCreated(ScheduleCreatedEvent event) {
        log.info("Publishing ScheduleCreatedEvent for scheduleId={}, screenId={}",
                event.getScheduleId(), event.getScreenId());

        kafkaTemplate.send(TOPIC, event.getScheduleId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ScheduleCreatedEvent: {}", ex.getMessage());
                    } else {
                        log.info("ScheduleCreatedEvent published successfully, offset={}",
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
