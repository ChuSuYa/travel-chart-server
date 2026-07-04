package com.travelchart.userservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelchart.userservice.entity.User;
import com.travelchart.userservice.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Inspiration point system.
 *
 * Point-earning rules:
 *   - Complete profile:      10 points
 *   - Generate plan:          5 points
 *   - Share plan:            15 points
 *   - Review POI:             3 points
 *   - Daily login:            1 point
 *
 * Deduction for PRO features will be determined by product later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspirationService {

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String INSPIRATION_LOG_KEY = "inspiration:log:";
    private static final String DAILY_LOGIN_KEY = "inspiration:daily:login:";
    private static final long LOG_TTL_DAYS = 30;

    // ---- public API ----

    /**
     * Add inspiration points for a given reason.
     */
    @Transactional
    public int addInspiration(Long userId, int amount, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        int current = user.getInspiration() != null ? user.getInspiration() : 0;
        int updated = current + amount;

        User update = new User();
        update.setUserId(userId);
        update.setInspiration(updated);
        userMapper.updateById(update);

        appendLog(userId, amount, "earn", reason);

        log.info("Added {} inspiration for userId={}, reason={}, newBalance={}", amount, userId, reason, updated);
        return updated;
    }

    /**
     * Get current inspiration balance.
     */
    public int getInspiration(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user.getInspiration() != null ? user.getInspiration() : 0;
    }

    /**
     * Deduct inspiration points (e.g. for PRO features).
     */
    @Transactional
    public int deductInspiration(Long userId, int amount, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        int current = user.getInspiration() != null ? user.getInspiration() : 0;
        if (current < amount) {
            throw new RuntimeException("灵感值不足，当前余额: " + current);
        }
        int updated = current - amount;

        User update = new User();
        update.setUserId(userId);
        update.setInspiration(updated);
        userMapper.updateById(update);

        appendLog(userId, -amount, "spend", reason);

        log.info("Deducted {} inspiration for userId={}, reason={}, newBalance={}", amount, userId, reason, updated);
        return updated;
    }

    /**
     * Daily login inspiration: awards 1 point once per calendar day.
     */
    @Transactional
    public int dailyLogin(Long userId) {
        String key = DAILY_LOGIN_KEY + userId + ":" + LocalDate.now();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return getInspiration(userId);
        }
        stringRedisTemplate.opsForValue().set(key, "1", 25, TimeUnit.HOURS);
        return addInspiration(userId, 1, "daily_login");
    }

    /**
     * Get inspiration history (recent 30 days).
     */
    public List<Map<String, Object>> getHistory(Long userId) {
        String key = INSPIRATION_LOG_KEY + userId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> history = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            return history;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse inspiration log for userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    // ---- internal helpers ----

    private void appendLog(Long userId, int amount, String type, String reason) {
        String key = INSPIRATION_LOG_KEY + userId;
        String json = stringRedisTemplate.opsForValue().get(key);

        List<Map<String, Object>> history;
        try {
            history = (json != null && !json.isEmpty())
                    ? objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {})
                    : new ArrayList<>();
        } catch (JsonProcessingException e) {
            history = new ArrayList<>();
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("amount", amount);
        entry.put("type", type);
        entry.put("reason", reason);
        entry.put("time", LocalDateTime.now().toString());
        history.add(0, entry); // prepend so most recent first

        // Keep only last 100 entries
        if (history.size() > 100) {
            history = history.subList(0, 100);
        }

        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(history),
                    LOG_TTL_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize inspiration log for userId={}", userId, e);
        }
    }
}
