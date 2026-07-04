package com.travelchart.weatherservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelchart.weatherservice.entity.WeatherSnapshot;
import com.travelchart.weatherservice.mapper.WeatherSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Core weather service.
 *
 * Data flow: Redis cache -> MySQL DB -> external API (WeatherApiClient) -> save to DB + Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherSnapshotMapper weatherSnapshotMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WeatherApiClient weatherApiClient;

    private static final String CACHE_KEY_PREFIX = "weather:plan:";
    private static final long CACHE_TTL = 1800; // 30 minutes

    // ================================================================
    //  Forecast retrieval (cache -> DB -> API -> persist)
    // ================================================================

    /**
     * Get or fetch weather forecast for a plan's city and date range.
     */
    public List<WeatherSnapshot> getOrFetchWeather(Long planId, String city, LocalDate startDate, LocalDate endDate) {
        String cacheKey = buildCacheKey(planId, city, startDate, endDate);

        // 1. Try Redis cache
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List) {
            @SuppressWarnings("unchecked")
            List<WeatherSnapshot> cachedList = (List<WeatherSnapshot>) cached;
            if (!cachedList.isEmpty()) {
                log.debug("Weather cache hit for key={}", cacheKey);
                return cachedList;
            }
        }

        // 2. Try MySQL database
        List<WeatherSnapshot> dbSnapshots = weatherSnapshotMapper.selectByPlanAndCity(planId, city);
        if (dbSnapshots != null && !dbSnapshots.isEmpty()) {
            // Filter to the requested date range
            List<WeatherSnapshot> filtered = dbSnapshots.stream()
                    .filter(s -> !s.getForecastDate().isBefore(startDate) && !s.getForecastDate().isAfter(endDate))
                    .collect(java.util.stream.Collectors.toList());
            if (!filtered.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, filtered, CACHE_TTL, TimeUnit.SECONDS);
                return filtered;
            }
        }

        // 3. Fetch from external API
        List<WeatherApiClient.WeatherResponse> apiResponses = weatherApiClient.fetch(city, startDate, endDate);
        List<WeatherSnapshot> snapshots = new ArrayList<>();

        for (WeatherApiClient.WeatherResponse resp : apiResponses) {
            WeatherSnapshot s = new WeatherSnapshot();
            s.setPlanId(planId);
            s.setCity(resp.getCity());
            s.setForecastDate(resp.getForecastDate());
            s.setTempHigh(resp.getTempHigh());
            s.setTempLow(resp.getTempLow());
            s.setConditionCode(resp.getConditionCode());
            s.setConditionText(resp.getConditionText());
            s.setHumidity(resp.getHumidity());
            s.setUvIndex(resp.getUvIndex());
            s.setAqi(resp.getAqi());
            s.setWindDirection(resp.getWindDirection());
            s.setWindSpeed(resp.getWindSpeed());
            s.setAlertLevel(resp.getAlertLevel());
            s.setAlertText(resp.getAlertText());

            // Check if we already have this snapshot in the DB
            List<WeatherSnapshot> existing = weatherSnapshotMapper.selectList(
                    new LambdaQueryWrapper<WeatherSnapshot>()
                            .eq(WeatherSnapshot::getPlanId, planId)
                            .eq(WeatherSnapshot::getCity, city)
                            .eq(WeatherSnapshot::getForecastDate, resp.getForecastDate())
            );
            if (existing != null && !existing.isEmpty()) {
                // Update existing
                s.setId(existing.get(0).getId());
                weatherSnapshotMapper.updateById(s);
            } else {
                // Insert new
                weatherSnapshotMapper.insert(s);
            }
            snapshots.add(s);
        }

        // Save to Redis
        redisTemplate.opsForValue().set(cacheKey, snapshots, CACHE_TTL, TimeUnit.SECONDS);
        log.info("Weather fetched and cached for planId={}, city={}, {} days", planId, city, snapshots.size());
        return snapshots;
    }

    // ================================================================
    //  Luggage checklist
    // ================================================================

    /**
     * Generate a detailed luggage checklist based on weather conditions.
     *
     * Categories:
     *   - Clothing: jacket/t-shirt/umbrella based on temp/weather
     *   - Accessories: sunscreen/sunglasses for UV > 5
     *   - Medicine: motion sickness/mosquito repellent for tropical
     *   - Electronics: power bank/adapter/relevant gear
     */
    public Map<String, Object> getLuggageChecklist(Long planId, String city, LocalDate startDate, LocalDate endDate) {
        List<WeatherSnapshot> snapshots = getOrFetchWeather(planId, city, startDate, endDate);
        Map<String, Object> checklist = new LinkedHashMap<>();

        // Aggregate weather stats
        int minTemp = snapshots.stream().mapToInt(WeatherSnapshot::getTempLow).min().orElse(20);
        int maxTemp = snapshots.stream().mapToInt(WeatherSnapshot::getTempHigh).max().orElse(30);
        int maxUv = snapshots.stream().mapToInt(WeatherSnapshot::getUvIndex).max().orElse(0);
        int maxHumidity = snapshots.stream().mapToInt(WeatherSnapshot::getHumidity).max().orElse(50);
        boolean hasRain = snapshots.stream().anyMatch(s -> isRainCode(s.getConditionCode()));
        boolean hasSnow = snapshots.stream().anyMatch(s -> "snow".equalsIgnoreCase(s.getConditionCode()));
        boolean hasStorm = snapshots.stream().anyMatch(s ->
                "storm".equalsIgnoreCase(s.getConditionCode()) || "heavy_rain".equalsIgnoreCase(s.getConditionCode()));
        boolean isHumid = maxHumidity >= 75;
        boolean isTropical = maxTemp >= 30 && maxHumidity >= 70;

        // ---- Clothing ----
        List<String> clothing = new ArrayList<>();
        if (maxTemp > 28) {
            clothing.add("短袖T恤、短裤、裙子");
            clothing.add("防晒衫/薄开衫");
        }
        if (maxTemp >= 20 && maxTemp <= 28) {
            clothing.add("长袖衬衫、薄外套");
            clothing.add("长裤、休闲裤");
        }
        if (minTemp >= 10 && minTemp < 20) {
            clothing.add("卫衣、薄针织衫");
            clothing.add("薄外套、风衣");
        }
        if (minTemp >= 5 && minTemp < 10) {
            clothing.add("毛衣、加绒卫衣");
            clothing.add("厚外套、夹克");
        }
        if (minTemp < 5) {
            clothing.add("羽绒服/棉服");
            clothing.add("保暖内衣、羊毛衫");
            clothing.add("手套、围巾、帽子");
        }
        if (hasRain) {
            clothing.add("雨伞/雨衣");
            clothing.add("防水鞋/雨靴");
        }
        if (hasSnow) {
            clothing.add("防滑靴、雪地鞋");
        }
        // General
        if (!hasRain) {
            clothing.add("运动鞋/舒适平底鞋");
        }
        clothing.add("换洗衣物（建议按天+1准备）、袜子、内衣");

        // ---- Accessories ----
        List<String> accessories = new ArrayList<>();
        if (maxUv >= 5) {
            accessories.add("防晒霜（SPF50+）");
            accessories.add("太阳镜");
            accessories.add("遮阳帽/渔夫帽");
        }
        if (hasStorm) {
            accessories.add("防水手机套");
            accessories.add("密封袋（保护电子设备）");
        }
        if (maxTemp < 10) {
            accessories.add("保温杯");
        }
        accessories.add("双肩包/斜挎包");

        // ---- Medicine ----
        List<String> medicine = new ArrayList<>();
        medicine.add("创可贴、碘伏棉签");
        medicine.add("感冒药、退烧药（布洛芬/对乙酰氨基酚）");
        if (isTropical) {
            medicine.add("防蚊液/驱蚊手环");
            medicine.add("藿香正气水（防中暑）");
            medicine.add("风油精/清凉油");
        }
        if (isHumid) {
            medicine.add("湿疹膏/皮炎平");
        }
        medicine.add("肠胃药（蒙脱石散/黄连素）");
        medicine.add("晕车药/晕船药（如需乘坐长途交通工具）");
        medicine.add("个人慢性病药（如降压药、胰岛素等）");

        // ---- Electronics ----
        List<String> electronics = new ArrayList<>();
        electronics.add("手机、充电器、数据线");
        electronics.add("移动电源/充电宝");
        if (hasRain || hasStorm) {
            electronics.add("防水手机壳");
        }
        electronics.add("相机及SD卡（如需拍照）");
        electronics.add("转换插头（如前往港澳/国外）");
        electronics.add("耳机（降噪耳机适合长途交通）");

        // ---- Documents ----
        List<String> documents = new ArrayList<>();
        documents.add("身份证/护照");
        documents.add("机票/火车票/酒店预订确认单");
        documents.add("银行卡/现金（适当备用）");
        documents.add("旅行保险单");

        // ---- Dressing advice ----
        String dressingAdvice = generateDressingAdvice(minTemp, maxTemp, hasRain, hasSnow);

        checklist.put("city", city);
        checklist.put("dateRange", startDate + " ~ " + endDate);
        checklist.put("tempRange", minTemp + "℃ ~ " + maxTemp + "℃");
        checklist.put("weatherSummary", summarizeWeather(snapshots));
        checklist.put("clothing", clothing);
        checklist.put("accessories", accessories);
        checklist.put("medicine", medicine);
        checklist.put("electronics", electronics);
        checklist.put("documents", documents);
        checklist.put("dressingAdvice", dressingAdvice);
        checklist.put("specialNotes", buildSpecialNotes(maxTemp, hasRain, hasStorm, isTropical));

        return checklist;
    }

    // ---- helpers ----

    private String buildCacheKey(Long planId, String city, LocalDate start, LocalDate end) {
        return CACHE_KEY_PREFIX + planId + ":" + city + ":" + start + ":" + end;
    }

    private boolean isRainCode(String code) {
        return "rain".equalsIgnoreCase(code)
                || "heavy_rain".equalsIgnoreCase(code)
                || "storm".equalsIgnoreCase(code);
    }

    private String summarizeWeather(List<WeatherSnapshot> snapshots) {
        Set<String> conditions = new LinkedHashSet<>();
        for (WeatherSnapshot s : snapshots) {
            conditions.add(s.getConditionText());
        }
        return String.join("、", conditions);
    }

    private String generateDressingAdvice(int minTemp, int maxTemp, boolean hasRain, boolean hasSnow) {
        StringBuilder sb = new StringBuilder();
        if (maxTemp > 30) {
            sb.append("天气炎热，建议穿着轻薄透气的衣物，外出做好防晒措施。");
        } else if (maxTemp > 25) {
            sb.append("天气舒适，推荐短袖或薄长袖，早晚体感微凉可备薄外套。");
        } else if (maxTemp > 18) {
            sb.append("天气凉爽，建议长袖衬衫或薄外套。");
        } else if (maxTemp > 10) {
            sb.append("天气较凉，建议穿着卫衣加薄外套，注意早晚温差。");
        } else {
            sb.append("天气寒冷，请穿着厚外套、羽绒服等保暖衣物，注意防寒。");
        }
        if (hasRain) {
            sb.append(" 雨天出行请携带雨具，穿防水防滑鞋。");
        }
        if (hasSnow) {
            sb.append(" 雪天请穿防滑鞋，注意路面湿滑。");
        }
        return sb.toString();
    }

    private List<String> buildSpecialNotes(int maxTemp, boolean hasRain, boolean hasStorm, boolean isTropical) {
        List<String> notes = new ArrayList<>();
        if (hasStorm) {
            notes.add("暴雨天气请注意安全，避免前往山区和低洼地带。");
        } else if (hasRain) {
            notes.add("雨天路面湿滑，步行和驾车请注意安全。");
        }
        if (isTropical) {
            notes.add("热带地区蚊虫较多，建议穿着长袖长裤以防蚊虫叮咬。");
            notes.add("天气闷热潮湿，注意补充水分，避免长时间户外暴晒。");
        }
        if (maxTemp > 35) {
            notes.add("高温预警：尽量避免中午12点至下午3点户外活动，谨防中暑。");
        }
        if (notes.isEmpty()) {
            notes.add("天气适宜出行，祝旅途愉快！");
        }
        return notes;
    }
}
