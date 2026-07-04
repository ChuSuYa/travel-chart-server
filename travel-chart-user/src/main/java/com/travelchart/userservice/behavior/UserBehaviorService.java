package com.travelchart.userservice.behavior;

import com.travelchart.userservice.entity.UserBehavior;
import com.travelchart.userservice.mapper.UserBehaviorMapper;
import com.travelchart.userservice.service.BehaviorProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * User behavior event recording service.
 * Each method inserts a row into tg_user_behavior AND sends a Kafka event
 * on the travel-behavior topic in the format the existing BehaviorConsumer expects.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorService {

    private final UserBehaviorMapper userBehaviorMapper;
    private final BehaviorProducer behaviorProducer;

    /**
     * Record a click event on a POI or target.
     */
    @Transactional
    public void recordClick(Long userId, Long poiId, String targetId, String metadata) {
        UserBehavior behavior = buildBehavior(userId, poiId, "click", targetId, metadata);
        userBehaviorMapper.insert(behavior);
        behaviorProducer.sendClick(poiId, userId);
        log.debug("Recorded click: userId={}, poiId={}, targetId={}", userId, poiId, targetId);
    }

    /**
     * Record a view event on a POI or target.
     */
    @Transactional
    public void recordView(Long userId, Long poiId, String targetId) {
        UserBehavior behavior = buildBehavior(userId, poiId, "view", targetId, null);
        userBehaviorMapper.insert(behavior);
        behaviorProducer.sendView(poiId, userId);
        log.debug("Recorded view: userId={}, poiId={}, targetId={}", userId, poiId, targetId);
    }

    /**
     * Record a favorite/bookmark event on a POI.
     */
    @Transactional
    public void recordFavorite(Long userId, Long poiId) {
        UserBehavior behavior = buildBehavior(userId, poiId, "fav", null, null);
        userBehaviorMapper.insert(behavior);
        behaviorProducer.sendFavorite(poiId, userId);
        log.debug("Recorded favorite: userId={}, poiId={}", userId, poiId);
    }

    /**
     * Record a plan share event.
     */
    @Transactional
    public void recordShare(Long userId, Long planId) {
        UserBehavior behavior = buildBehavior(userId, null, "share", String.valueOf(planId), null);
        userBehaviorMapper.insert(behavior);
        behaviorProducer.sendShare(planId, userId);
        log.debug("Recorded share: userId={}, planId={}", userId, planId);
    }

    private UserBehavior buildBehavior(Long userId, Long poiId, String actionType,
                                        String targetId, String metadata) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setPoiId(poiId);
        behavior.setActionType(actionType);
        behavior.setTargetId(targetId);
        behavior.setMetadata(metadata);
        behavior.setEventTime(LocalDateTime.now());
        return behavior;
    }
}
