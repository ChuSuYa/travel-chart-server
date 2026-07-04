package com.travelchart.weatherservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelchart.weatherservice.entity.ReminderLog;
import com.travelchart.weatherservice.entity.WeatherSnapshot;
import com.travelchart.weatherservice.mapper.ReminderLogMapper;
import com.travelchart.weatherservice.mapper.WeatherSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Timed reminder engine for pre-trip, daily motivation, and weather alerts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderLogMapper reminderLogMapper;
    private final WeatherSnapshotMapper weatherSnapshotMapper;

    private static final ThreadLocalRandom RNG = ThreadLocalRandom.current();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ---- inspiring travel quotes ----
    private static final String[] TRAVEL_QUOTES = {
            "\"旅行不是逃避，而是发现另一个自己。\"",
            "\"世界是一本书，不旅行的人只读了其中一页。\" — 圣奥古斯丁",
            "\"生活不是等待暴风雨过去，而是学会在雨中跳舞。\"",
            "\"最好的旅行，是在陌生的地方，找到久违的感动。\"",
            "\"要么读书，要么旅行，身体和灵魂总有一个在路上。\"",
            "\"旅途中最美的风景，往往是路上不期而遇的温暖。\"",
            "\"走出去，世界就在眼前；走不出去，眼前就是世界。\"",
            "\"旅行教会我们，幸福不是拥有更多，而是需要更少。\"",
            "\"每一个不曾旅行的日子，都是对青春的辜负。\"",
            "\"不是为了逃离生活，而是让生活不逃离我们。\""
    };

    /**
     * Daily scheduled check for plans starting in 3 or 1 days.
     * Creates pre-trip reminder logs for plans with upcoming departure dates.
     */
    @Scheduled(cron = "${weather.scheduled.pre-trip-cron:0 0 8 * * ?}")
    public void checkPreTripReminders() {
        log.info("[Scheduled] Pre-trip reminder check started");

        // Query weather snapshots that have plans starting in 3 or 1 days
        LocalDate today = LocalDate.now();
        LocalDate threeDaysLater = today.plusDays(3);
        LocalDate oneDayLater = today.plusDays(1);

        // Find weather snapshots for plans with forecast dates 3 days ahead
        List<WeatherSnapshot> snapshots3d = weatherSnapshotMapper.selectList(
                new LambdaQueryWrapper<WeatherSnapshot>()
                        .eq(WeatherSnapshot::getForecastDate, threeDaysLater)
        );
        processPreTripReminders(snapshots3d, "PRE_TRIP_3D", "出发倒计时3天", threeDaysLater);

        // Find weather snapshots for plans with forecast dates 1 day ahead
        List<WeatherSnapshot> snapshots1d = weatherSnapshotMapper.selectList(
                new LambdaQueryWrapper<WeatherSnapshot>()
                        .eq(WeatherSnapshot::getForecastDate, oneDayLater)
        );
        processPreTripReminders(snapshots1d, "PRE_TRIP_1D", "出发倒计时1天", oneDayLater);

        log.info("[Scheduled] Pre-trip reminder check completed");
    }

    private void processPreTripReminders(List<WeatherSnapshot> snapshots, String type,
                                          String titleTemplate, LocalDate departureDate) {
        // Group snapshots by planId
        Map<Long, List<WeatherSnapshot>> byPlan = snapshots.stream()
                .collect(Collectors.groupingBy(WeatherSnapshot::getPlanId));

        for (Map.Entry<Long, List<WeatherSnapshot>> entry : byPlan.entrySet()) {
            Long planId = entry.getKey();
            List<WeatherSnapshot> days = entry.getValue();
            if (days.isEmpty()) continue;

            // Build a summary
            WeatherSnapshot day = days.get(0);
            String city = day.getCity();
            String weatherText = day.getConditionText();
            int high = day.getTempHigh();
            int low = day.getTempLow();

            String title = titleTemplate;
            String content = String.format(
                    "距离%s出发还有%d天！目的地%s当天天气：%s，温度%d℃~%d℃。%s",
                    city,
                    type.equals("PRE_TRIP_3D") ? 3 : 1,
                    city,
                    weatherText,
                    low, high,
                    generatePackingReminder(high, low, weatherText)
            );

            // Check for duplicate today
            boolean exists = reminderLogMapper.selectCount(
                    new LambdaQueryWrapper<ReminderLog>()
                            .eq(ReminderLog::getPlanId, planId)
                            .eq(ReminderLog::getReminderType, type)
                            .ge(ReminderLog::getCreateTime, LocalDate.now().atStartOfDay())
            ) > 0;

            if (exists) {
                log.debug("Pre-trip reminder already sent today for planId={}, type={}", planId, type);
                continue;
            }

            pushReminder(planId, 1L, type, title, content);
        }
    }

    /**
     * Generate a daily motivation message personalized to the weather.
     */
    public Map<String, String> generateDailyMotivation(Long planId, String city) {
        // Get current weather
        List<WeatherSnapshot> snapshots = weatherSnapshotMapper.selectList(
                new LambdaQueryWrapper<WeatherSnapshot>()
                        .eq(WeatherSnapshot::getPlanId, planId)
                        .eq(WeatherSnapshot::getCity, city)
                        .eq(WeatherSnapshot::getForecastDate, LocalDate.now())
        );

        String conditionText;
        int tempHigh, tempLow;
        if (snapshots != null && !snapshots.isEmpty()) {
            WeatherSnapshot today = snapshots.get(0);
            conditionText = today.getConditionText();
            tempHigh = today.getTempHigh();
            tempLow = today.getTempLow();
        } else {
            conditionText = "晴";
            tempHigh = 25;
            tempLow = 15;
        }

        String quote = TRAVEL_QUOTES[RNG.nextInt(TRAVEL_QUOTES.length)];
        String weatherAdvice;

        switch (conditionText) {
            case "晴":
            case "晴转多云":
                weatherAdvice = String.format("今日%s天气晴好，温度%d~%d℃，适合出游！", quote, tempLow, tempHigh);
                break;
            case "多云":
            case "阴":
                weatherAdvice = String.format("今天%s多云，温度%d~%d℃，适合户外活动，记得带件薄外套。",
                        quote, tempLow, tempHigh);
                break;
            case "小雨":
            case "大雨":
            case "暴雨":
                weatherAdvice = String.format("今天有%s，温度%d~%d℃，建议穿防水鞋，室内景点也是不错的选择。%s",
                        conditionText, tempLow, tempHigh, quote);
                break;
            case "雪":
                weatherAdvice = String.format("冬日飘雪，温度%d~%d℃，记得保暖哦。%s",
                        tempLow, tempHigh, quote);
                break;
            case "雾":
                weatherAdvice = String.format("今日有雾，能见度较低，出行请注意安全。温度%d~%d℃。%s",
                        tempLow, tempHigh, quote);
                break;
            default:
                weatherAdvice = String.format("今日%s，温度%d~%d℃。%s",
                        conditionText, tempLow, tempHigh, quote);
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("date", LocalDate.now().format(DATE_FMT));
        result.put("conditionText", conditionText);
        result.put("tempHigh", String.valueOf(tempHigh));
        result.put("tempLow", String.valueOf(tempLow));
        result.put("message", weatherAdvice);
        result.put("quote", quote);
        return result;
    }

    /**
     * Generate the daily motivation broadcast (called by the scheduled task).
     * This crafts encouragement messages mixed with inspirational quotes.
     */
    @Scheduled(cron = "${weather.scheduled.daily-morning-cron:0 0 7 * * ?}")
    public void generateDailyMotivation() {
        log.info("[Scheduled] Daily morning motivation started");

        // Find all active plans that have weather data for today
        List<WeatherSnapshot> todaysWeather = weatherSnapshotMapper.selectList(
                new LambdaQueryWrapper<WeatherSnapshot>()
                        .eq(WeatherSnapshot::getForecastDate, LocalDate.now())
        );

        Set<Long> processedPlans = new HashSet<>();
        for (WeatherSnapshot snap : todaysWeather) {
            Long planId = snap.getPlanId();
            if (processedPlans.contains(planId)) continue;
            processedPlans.add(planId);

            Map<String, String> motivation = generateDailyMotivation(planId, snap.getCity());

            // Check for duplicate today
            boolean exists = reminderLogMapper.selectCount(
                    new LambdaQueryWrapper<ReminderLog>()
                            .eq(ReminderLog::getPlanId, planId)
                            .eq(ReminderLog::getReminderType, "DAILY_MORNING")
                            .ge(ReminderLog::getCreateTime, LocalDate.now().atStartOfDay())
            ) > 0;

            if (exists) {
                log.debug("Daily motivation already sent today for planId={}", planId);
                continue;
            }

            pushReminder(planId, 1L, "DAILY_MORNING", "今日游玩建议", motivation.get("message"));
        }

        log.info("[Scheduled] Daily morning motivation completed, processed {} plans", processedPlans.size());
    }

    /**
     * Scheduled every 30 minutes to detect alert-level weather and create urgent reminders.
     */
    @Scheduled(cron = "${weather.scheduled.alert-check-cron:0 */30 * * * ?}")
    public void checkWeatherAlerts() {
        log.debug("[Scheduled] Weather alert check started");

        // Check weather snapshots for today and tomorrow that have alert levels
        List<WeatherSnapshot> alerts = weatherSnapshotMapper.selectList(
                new LambdaQueryWrapper<WeatherSnapshot>()
                        .ge(WeatherSnapshot::getForecastDate, LocalDate.now())
                        .le(WeatherSnapshot::getForecastDate, LocalDate.now().plusDays(2))
                        .isNotNull(WeatherSnapshot::getAlertLevel)
                        .ne(WeatherSnapshot::getAlertLevel, "")
        );

        for (WeatherSnapshot snap : alerts) {
            // Check for duplicate alert today
            String dedupKey = snap.getPlanId() + "_" + snap.getCity() + "_" + snap.getAlertLevel();
            boolean exists = reminderLogMapper.selectCount(
                    new LambdaQueryWrapper<ReminderLog>()
                            .eq(ReminderLog::getPlanId, snap.getPlanId())
                            .eq(ReminderLog::getReminderType, "ALERT")
                            .like(ReminderLog::getContent, dedupKey)
                            .ge(ReminderLog::getCreateTime, LocalDate.now().atStartOfDay())
            ) > 0;

            if (exists) {
                log.debug("Alert already sent today for planId={}, city={}, alert={}",
                        snap.getPlanId(), snap.getCity(), snap.getAlertLevel());
                continue;
            }

            String title = String.format("⚠ %s天气预警", snap.getCity());
            String content = String.format("%s (%s): %s。温度%d~%d℃，请提前做好准备。[%s]",
                    snap.getCity(),
                    snap.getForecastDate().format(DATE_FMT),
                    snap.getAlertText(),
                    snap.getTempLow(),
                    snap.getTempHigh(),
                    dedupKey
            );

            pushReminder(snap.getPlanId(), 1L, "ALERT", title, content);
        }

        log.debug("[Scheduled] Weather alert check completed, processed {} alerts", alerts.size());
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
        log.info("Reminder pushed: planId={}, userId={}, type={}, title={}", planId, userId, type, title);
    }

    private String generatePackingReminder(int high, int low, String weatherText) {
        StringBuilder sb = new StringBuilder();
        if (low < 5) sb.append("天气寒冷，请带好羽绒服和保暖衣物。");
        else if (low < 15) sb.append("早晚温度较低，建议带薄外套。");
        else if (high > 30) sb.append("天气炎热，注意防晒补水。");

        if (weatherText.contains("雨")) sb.append("雨天出行，记得带伞，穿防水鞋。");
        if (weatherText.contains("雪")) sb.append("雪天路滑，注意防摔防滑。");
        if (sb.length() == 0) sb.append("天气适宜出行，祝旅途愉快！");
        return sb.toString();
    }
}
