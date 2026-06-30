package com.travelchart.userservice.service;

import com.travelchart.userservice.dto.BehaviorEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BehaviorProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC = "travel-behavior";

    private void send(BehaviorEvent event) {
        String message = String.format("%s|%s|%s|%s",
                event.getType(),
                event.getPoiId() != null ? event.getPoiId() : "",
                event.getUserId() != null ? event.getUserId() : "",
                event.getTimestamp() != null ? event.getTimestamp() : System.currentTimeMillis());
        kafkaTemplate.send(TOPIC, message);
    }

    public void sendClick(Long poiId, Long userId) {
        send(new BehaviorEvent("click", poiId, userId, System.currentTimeMillis()));
    }

    public void sendView(Long poiId, Long userId) {
        send(new BehaviorEvent("view", poiId, userId, System.currentTimeMillis()));
    }

    public void sendFavorite(Long poiId, Long userId) {
        send(new BehaviorEvent("fav", poiId, userId, System.currentTimeMillis()));
    }

    public void sendShare(Long poiId, Long userId) {
        send(new BehaviorEvent("share", poiId, userId, System.currentTimeMillis()));
    }
}
