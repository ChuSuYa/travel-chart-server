package com.travelchart.weatherservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelchart.weatherservice.entity.ReminderLog;
import com.travelchart.weatherservice.entity.WeatherSnapshot;
import com.travelchart.weatherservice.mapper.ReminderLogMapper;
import com.travelchart.weatherservice.mapper.WeatherSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherSnapshotMapper weatherSnapshotMapper;
    private final ReminderLogMapper reminderLogMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String CACHE_KEY_PREFIX = "weather:plan:";
    private static final long CACHE_TTL = 1800;

    public List<WeatherSnapshot> getOrFetchWeather(Long planId, String city, LocalDate startDate, LocalDate endDate) {
        String cacheKey = CACHE_KEY_PREFIX + planId + ":" + city + ":" + startDate + ":" + endDate;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List) {
            return (List<WeatherSnapshot>) cached;
        }

        List<WeatherSnapshot> snapshots = weatherSnapshotMapper.selectByPlanAndCity(planId, city);
        if (snapshots != null && !snapshots.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, snapshots, CACHE_TTL, TimeUnit.SECONDS);
            return snapshots;
        }

        List<WeatherSnapshot> fetched = fetchFromApi(city, startDate, endDate);
        for (WeatherSnapshot s : fetched) {
            s.setPlanId(planId);
            weatherSnapshotMapper.insert(s);
        }

        snapshots = weatherSnapshotMapper.selectByPlanAndCity(planId, city);
        redisTemplate.opsForValue().set(cacheKey, snapshots, CACHE_TTL, TimeUnit.SECONDS);
        return snapshots;
    }

    private List<WeatherSnapshot> fetchFromApi(String city, LocalDate start, LocalDate end) {
        List<WeatherSnapshot> result = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            WeatherSnapshot s = new WeatherSnapshot();
            s.setCity(city);
            s.setForecastDate(current);
            s.setTempHigh(28);
            s.setTempLow(20);
            s.setConditionCode("sunny");
            s.setConditionText("晴");
            s.setHumidity(55);
            s.setUvIndex(6);
            s.setAqi(42);
            s.setWindDirection("东南");
            s.setWindSpeed("3级");
            result.add(s);
            current = current.plusDays(1);
        }
        return result;
    }

    @Scheduled(cron = "${weather.scheduled.pre-trip-cron:0 0 8 * * ?}")
    public void checkPreTripWeather() {
        log.info("[Scheduled] Pre-trip weather check started");
        pushReminder(1L, 1L, "PRE_TRIP_3D", "出发倒计时3天", "距离出行还有3天，目的地天气晴好，温度22-30℃");
    }

    @Scheduled(cron = "${weather.scheduled.daily-morning-cron:0 0 7 * * ?}")
    public void dailyMorningAdvice() {
        log.info("[Scheduled] Daily morning weather advice started");
        pushReminder(1L, 1L, "DAILY_MORNING", "今日游玩建议", "今天天气晴，适合户外活动，建议上午游览室外景点");
    }

    @Scheduled(cron = "${weather.scheduled.alert-check-cron:0 */30 * * * ?}")
    public void checkWeatherAlerts() {
        log.info("[Scheduled] Weather alert check started");
    }

    private void pushReminder(Long planId, Long userId, String type, String title, String content) {
        ReminderLog reminderLog = new ReminderLog();
        reminderLog.setPlanId(planId);
        reminderLog.setUserId(userId);
        reminderLog.setReminderType(type);
        reminderLog.setTitle(title);
        reminderLog.setContent(content);
        reminderLog.setIsRead(0);
        reminderLog.setPushStatus("sent");
        reminderLogMapper.insert(reminderLog);
        log.info("Reminder pushed: planId={}, userId={}, type={}", planId, userId, type);
    }

    public Map<String, Object> getLuggageChecklist(Long planId, String city, LocalDate startDate, LocalDate endDate) {
        List<WeatherSnapshot> snapshots = getOrFetchWeather(planId, city, startDate, endDate);
        Map<String, Object> checklist = new LinkedHashMap<>();
        List<String> items = new ArrayList<>();

        int minTemp = snapshots.stream().mapToInt(WeatherSnapshot::getTempLow).min().orElse(20);
        int maxTemp = snapshots.stream().mapToInt(WeatherSnapshot::getTempHigh).max().orElse(30);
        int maxUv = snapshots.stream().mapToInt(WeatherSnapshot::getUvIndex).max().orElse(0);

        if (maxTemp > 28) items.add("短袖T恤、短裤、防晒衫");
        if (minTemp < 15) items.add("薄外套、长裤、围巾");
        if (minTemp < 5) items.add("羽绒服、保暖内衣、手套");
        if (maxTemp >= 20 && maxTemp <= 25) items.add("薄外套、长袖衬衫");
        if (snapshots.stream().anyMatch(s -> "rain".equalsIgnoreCase(s.getConditionCode()))) {
            items.add("雨伞/雨衣、防水鞋");
        }
        if (maxUv >= 5) items.add("防晒霜、太阳镜、遮阳帽");
        items.add("常备药品、充电器/移动电源、身份证件");

        checklist.put("city", city);
        checklist.put("dateRange", startDate + " - " + endDate);
        checklist.put("tempRange", minTemp + "℃ ~ " + maxTemp + "℃");
        checklist.put("items", items);
        checklist.put("dressingAdvice", generateDressingAdvice(minTemp, maxTemp));
        return checklist;
    }

    private String generateDressingAdvice(int minTemp, int maxTemp) {
        if (maxTemp > 30) return "天气炎热，建议穿着轻薄透气的衣物，注意防晒补水。";
        if (maxTemp > 25) return "天气舒适，建议穿着短袖或薄长袖，早晚可备薄外套。";
        if (maxTemp > 18) return "天气凉爽，建议穿着长袖衬衫或薄外套。";
        if (maxTemp > 10) return "天气较凉，建议穿着卫衣加薄外套。";
        return "天气寒冷，建议穿着厚外套，注意保暖。";
    }
}
