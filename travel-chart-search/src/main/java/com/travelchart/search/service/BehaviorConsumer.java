package com.travelchart.search.service;

import com.travelchart.search.entity.PoiDocument;
import com.travelchart.search.repository.PoiSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class BehaviorConsumer {

    private static final Logger log = LoggerFactory.getLogger(BehaviorConsumer.class);

    @Autowired
    private PoiSearchRepository poiSearchRepository;

    @KafkaListener(topics = "travel-behavior", groupId = "search-consumer-group")
    public void consumeBehavior(String message) {
        log.info("Received behavior event: {}", message);

        try {
            // message format: type|poiId|userId|timestamp
            String[] parts = message.split("\\|");
            if (parts.length < 2) return;

            String type = parts[0];
            Long poiId = Long.valueOf(parts[1]);

            Optional<PoiDocument> optDoc = poiSearchRepository.findById(poiId);
            if (optDoc.isEmpty()) return;

            PoiDocument doc = optDoc.get();
            double increment;

            switch (type) {
                case "click":
                    increment = 1.0;
                    break;
                case "view":
                    increment = 0.3;
                    break;
                case "fav":
                    increment = 10.0;
                    break;
                case "share":
                    increment = 8.0;
                    break;
                default:
                    return;
            }

            doc.setHeatScore((doc.getHeatScore() != null ? doc.getHeatScore() : 0) + increment);
            poiSearchRepository.save(doc);

            log.info("Updated heatScore for POI {}: +{} (type={})", poiId, increment, type);
        } catch (Exception e) {
            log.error("Failed to process behavior event: {}", message, e);
        }
    }
}
