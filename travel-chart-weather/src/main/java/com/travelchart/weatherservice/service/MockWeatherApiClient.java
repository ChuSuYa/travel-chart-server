package com.travelchart.weatherservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock weather API client that returns realistic, seasonally-appropriate weather
 * for 7 major Chinese tourist cities.
 *
 * Weather patterns are designed to match real climatological norms:
 * - Beijing:   Jan cold/dry, Apr mild/dusty,  Jul hot/humid,    Oct cool/clear
 * - Shanghai:  Jan cold/damp, Apr warm/rainy, Jul hot/steamy,   Oct mild/dry
 * - Hangzhou:  similar to Shanghai but wetter overall
 * - Chengdu:   mostly cloudy year-round, humid
 * - Xi'an:     dry, cold winter, hot summer
 * - Dali:      mild year-round, distinct dry/wet seasons
 * - Sanya:     tropical, hot year-round, summer afternoon thunderstorms
 */
@Slf4j
@Component
public class MockWeatherApiClient implements WeatherApiClient {

    private static final ThreadLocalRandom RNG = ThreadLocalRandom.current();

    @Override
    public List<WeatherResponse> fetch(String city, LocalDate startDate, LocalDate endDate) {
        List<WeatherResponse> results = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            results.add(buildForDay(city, current));
            current = current.plusDays(1);
        }
        return results;
    }

    // ---- city-specific pattern tables ----

    private WeatherResponse buildForDay(String city, LocalDate date) {
        CityClimate climate = getClimate(city);
        Month month = date.getMonth();
        SeasonPattern pattern = climate.monthPatterns.getOrDefault(month, climate.defaultPattern);

        // Base temp range with daily variation
        int baseHigh = pattern.baseHigh + dailyVariation(3);
        int baseLow  = pattern.baseLow  + dailyVariation(2);

        // Condition: pick from weighted set
        String conditionCode = pickWeighted(pattern.conditions);
        String conditionText = codeToText(conditionCode);

        // Derived fields
        int humidity = pattern.baseHumidity + dailyVariation(10);
        humidity = Math.max(10, Math.min(100, humidity));

        int uvIndex = conditionCode.equals("rain") || conditionCode.equals("cloudy")
                ? Math.max(0, pattern.baseUv - RNG.nextInt(3))
                : pattern.baseUv + dailyVariation(2);
        uvIndex = Math.max(0, Math.min(12, uvIndex));

        int aqi = pattern.baseAqi + dailyVariation(20);
        aqi = Math.max(10, Math.min(300, aqi));

        String windDir = pickWeighted(climate.windDirections);
        String windSpeed = pickWeighted(climate.windSpeeds);

        // Alert detection
        String alertLevel = null;
        String alertText = null;
        if (conditionCode.equals("storm") || conditionCode.equals("heavy_rain")) {
            alertLevel = "orange";
            alertText = "暴雨预警，请减少户外活动";
        } else if (conditionCode.equals("snow") && baseLow <= -5) {
            alertLevel = "yellow";
            alertText = "寒潮预警，请注意防寒保暖";
        } else if (conditionCode.equals("fog")) {
            alertLevel = "yellow";
            alertText = "大雾预警，能见度较低，出行注意安全";
        } else if (tempHigh(date, pattern) >= 38) {
            alertLevel = "yellow";
            alertText = "高温预警，请做好防暑降温措施";
        }

        WeatherResponse r = new WeatherResponse();
        r.setCity(city);
        r.setForecastDate(date);
        r.setTempHigh(baseHigh);
        r.setTempLow(baseLow);
        r.setConditionCode(conditionCode);
        r.setConditionText(conditionText);
        r.setHumidity(humidity);
        r.setUvIndex(uvIndex);
        r.setAqi(aqi);
        r.setWindDirection(windDir);
        r.setWindSpeed(windSpeed);
        r.setAlertLevel(alertLevel);
        r.setAlertText(alertText);
        return r;
    }

    // ---- helper: clamped temp ----

    private int tempHigh(LocalDate date, SeasonPattern p) {
        return p.baseHigh + dailyVariation(3);
    }

    // ---- city climate registry ----

    private CityClimate getClimate(String city) {
        return CLIMATES.getOrDefault(city, CLIMATES.get("上海"));
    }

    // ---- util ----

    private int dailyVariation(int range) {
        return RNG.nextInt(-range, range + 1);
    }

    private <T> T pickWeighted(Map<T, Integer> weighted) {
        int total = weighted.values().stream().mapToInt(Integer::intValue).sum();
        int roll = RNG.nextInt(total);
        for (Map.Entry<T, Integer> e : weighted.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return weighted.keySet().iterator().next();
    }

    private <T> T pickWeighted(List<T> flat) {
        return flat.get(RNG.nextInt(flat.size()));
    }

    private String codeToText(String code) {
        switch (code) {
            case "sunny":     return "晴";
            case "cloudy":    return "多云";
            case "overcast":  return "阴";
            case "rain":      return "小雨";
            case "heavy_rain":return "大雨";
            case "storm":     return "暴雨";
            case "snow":      return "雪";
            case "fog":       return "雾";
            default:          return "多云";
        }
    }

    // ========================================================
    //  Climate data (based on real climatological norms)
    // ========================================================

    private static final Map<String, CityClimate> CLIMATES = new LinkedHashMap<>();

    static {
        // --- Beijing ---
        CityClimate beijing = new CityClimate();
        beijing.defaultPattern = new SeasonPattern(20, 10,
                weightedMap("sunny,40", "cloudy,30", "overcast,15", "rain,10", "fog,5"),
                50, 5, 60);
        beijing.monthPatterns.put(Month.JANUARY,   new SeasonPattern(2, -7,  weightedMap("sunny,50", "cloudy,30", "overcast,10", "snow,10"), 35, 2, 80));
        beijing.monthPatterns.put(Month.FEBRUARY,  new SeasonPattern(6, -4, weightedMap("sunny,45", "cloudy,30", "overcast,10", "snow,10", "fog,5"), 35, 3, 70));
        beijing.monthPatterns.put(Month.MARCH,     new SeasonPattern(13, 1, weightedMap("sunny,40", "cloudy,30", "overcast,15", "rain,10", "fog,5"), 40, 4, 80));
        beijing.monthPatterns.put(Month.APRIL,     new SeasonPattern(21, 9, weightedMap("sunny,35", "cloudy,35", "overcast,15", "rain,10", "fog,5"), 45, 5, 90));
        beijing.monthPatterns.put(Month.MAY,       new SeasonPattern(27, 15,weightedMap("sunny,40", "cloudy,30", "overcast,15", "rain,15"), 50, 7, 70));
        beijing.monthPatterns.put(Month.JUNE,      new SeasonPattern(31, 20,weightedMap("sunny,30", "cloudy,30", "overcast,20", "rain,15", "heavy_rain,5"), 60, 8, 60));
        beijing.monthPatterns.put(Month.JULY,      new SeasonPattern(32, 23,weightedMap("sunny,20", "cloudy,25", "overcast,25", "rain,20", "heavy_rain,10"), 75, 9, 50));
        beijing.monthPatterns.put(Month.AUGUST,    new SeasonPattern(31, 22,weightedMap("sunny,25", "cloudy,25", "overcast,20", "rain,20", "heavy_rain,10"), 75, 8, 50));
        beijing.monthPatterns.put(Month.SEPTEMBER, new SeasonPattern(27, 16,weightedMap("sunny,40", "cloudy,30", "overcast,15", "rain,15"), 60, 6, 60));
        beijing.monthPatterns.put(Month.OCTOBER,   new SeasonPattern(20, 8, weightedMap("sunny,55", "cloudy,25", "overcast,10", "rain,10"), 50, 4, 55));
        beijing.monthPatterns.put(Month.NOVEMBER,  new SeasonPattern(11, 1, weightedMap("sunny,45", "cloudy,30", "overcast,15", "rain,10"), 45, 3, 70));
        beijing.monthPatterns.put(Month.DECEMBER,  new SeasonPattern(5, -5, weightedMap("sunny,55", "cloudy,25", "overcast,10", "snow,10"), 40, 2, 80));
        beijing.windDirections = weightedDirList("北,30", "西北,25", "南,20", "东南,15", "东北,10");
        beijing.windSpeeds = weightedSpeedList("2级,20", "3级,50", "4级,20", "5级,10");
        CLIMATES.put("北京", beijing);

        // --- Shanghai ---
        CityClimate shanghai = new CityClimate();
        shanghai.defaultPattern = new SeasonPattern(23, 15,
                weightedMap("sunny,25", "cloudy,35", "overcast,20", "rain,15", "fog,5"),
                65, 5, 50);
        shanghai.monthPatterns.put(Month.JANUARY,   new SeasonPattern(8, 2,  weightedMap("sunny,30", "cloudy,35", "overcast,20", "rain,15"), 65, 2, 55));
        shanghai.monthPatterns.put(Month.FEBRUARY,  new SeasonPattern(10, 4, weightedMap("sunny,25", "cloudy,35", "overcast,20", "rain,20"), 65, 3, 50));
        shanghai.monthPatterns.put(Month.MARCH,     new SeasonPattern(14, 7, weightedMap("sunny,20", "cloudy,30", "overcast,25", "rain,20", "fog,5"), 68, 4, 50));
        shanghai.monthPatterns.put(Month.APRIL,     new SeasonPattern(20, 12,weightedMap("sunny,20", "cloudy,30", "overcast,25", "rain,25"), 70, 6, 45));
        shanghai.monthPatterns.put(Month.MAY,       new SeasonPattern(25, 17,weightedMap("sunny,20", "cloudy,30", "overcast,25", "rain,25"), 72, 7, 50));
        shanghai.monthPatterns.put(Month.JUNE,      new SeasonPattern(28, 21,weightedMap("sunny,10", "cloudy,25", "overcast,30", "rain,25", "heavy_rain,10"), 80, 8, 45));
        shanghai.monthPatterns.put(Month.JULY,      new SeasonPattern(33, 26,weightedMap("sunny,15", "cloudy,25", "overcast,25", "rain,20", "heavy_rain,10", "storm,5"), 82, 10, 40));
        shanghai.monthPatterns.put(Month.AUGUST,    new SeasonPattern(32, 26,weightedMap("sunny,15", "cloudy,25", "overcast,25", "rain,20", "heavy_rain,10", "storm,5"), 80, 9, 40));
        shanghai.monthPatterns.put(Month.SEPTEMBER, new SeasonPattern(28, 22,weightedMap("sunny,20", "cloudy,30", "overcast,25", "rain,20", "heavy_rain,5"), 75, 7, 45));
        shanghai.monthPatterns.put(Month.OCTOBER,   new SeasonPattern(23, 16,weightedMap("sunny,35", "cloudy,35", "overcast,15", "rain,15"), 68, 5, 50));
        shanghai.monthPatterns.put(Month.NOVEMBER,  new SeasonPattern(17, 10,weightedMap("sunny,30", "cloudy,35", "overcast,20", "rain,15"), 65, 4, 50));
        shanghai.monthPatterns.put(Month.DECEMBER,  new SeasonPattern(11, 4, weightedMap("sunny,35", "cloudy,35", "overcast,15", "rain,15"), 65, 3, 55));
        shanghai.windDirections = weightedDirList("东南,35", "东,25", "北,20", "南,20");
        shanghai.windSpeeds = weightedSpeedList("2级,30", "3级,50", "4级,20");
        CLIMATES.put("上海", shanghai);

        // --- Hangzhou ---
        CityClimate hangzhou = new CityClimate();
        hangzhou.defaultPattern = new SeasonPattern(23, 15,
                weightedMap("sunny,20", "cloudy,30", "overcast,25", "rain,20", "fog,5"),
                70, 5, 45);
        hangzhou.monthPatterns.put(Month.JANUARY,   new SeasonPattern(9, 3,  weightedMap("sunny,25", "cloudy,35", "overcast,25", "rain,15"), 70, 2, 55));
        hangzhou.monthPatterns.put(Month.FEBRUARY,  new SeasonPattern(11, 5, weightedMap("sunny,20", "cloudy,35", "overcast,25", "rain,20"), 72, 3, 50));
        hangzhou.monthPatterns.put(Month.MARCH,     new SeasonPattern(15, 8, weightedMap("sunny,15", "cloudy,30", "overcast,30", "rain,25"), 75, 4, 48));
        hangzhou.monthPatterns.put(Month.APRIL,     new SeasonPattern(21, 13,weightedMap("sunny,15", "cloudy,30", "overcast,25", "rain,30"), 75, 6, 42));
        hangzhou.monthPatterns.put(Month.MAY,       new SeasonPattern(26, 18,weightedMap("sunny,15", "cloudy,30", "overcast,25", "rain,30"), 78, 7, 45));
        hangzhou.monthPatterns.put(Month.JUNE,      new SeasonPattern(29, 22,weightedMap("cloudy,20", "overcast,30", "rain,30", "heavy_rain,20"), 82, 8, 40));
        hangzhou.monthPatterns.put(Month.JULY,      new SeasonPattern(34, 27,weightedMap("sunny,10", "cloudy,25", "overcast,25", "rain,25", "heavy_rain,10", "storm,5"), 82, 10, 38));
        hangzhou.monthPatterns.put(Month.AUGUST,    new SeasonPattern(33, 26,weightedMap("sunny,10", "cloudy,25", "overcast,25", "rain,25", "heavy_rain,10", "storm,5"), 80, 9, 38));
        hangzhou.monthPatterns.put(Month.SEPTEMBER, new SeasonPattern(28, 22,weightedMap("sunny,15", "cloudy,30", "overcast,25", "rain,25", "heavy_rain,5"), 78, 7, 42));
        hangzhou.monthPatterns.put(Month.OCTOBER,   new SeasonPattern(24, 17,weightedMap("sunny,30", "cloudy,35", "overcast,20", "rain,15"), 72, 5, 48));
        hangzhou.monthPatterns.put(Month.NOVEMBER,  new SeasonPattern(18, 11,weightedMap("sunny,25", "cloudy,35", "overcast,25", "rain,15"), 70, 4, 50));
        hangzhou.monthPatterns.put(Month.DECEMBER,  new SeasonPattern(12, 5, weightedMap("sunny,30", "cloudy,35", "overcast,20", "rain,15"), 68, 3, 52));
        hangzhou.windDirections = weightedDirList("东南,30", "东,25", "北,25", "南,20");
        hangzhou.windSpeeds = weightedSpeedList("2级,35", "3级,50", "4级,15");
        CLIMATES.put("杭州", hangzhou);

        // --- Chengdu ---
        CityClimate chengdu = new CityClimate();
        chengdu.defaultPattern = new SeasonPattern(23, 15,
                weightedMap("cloudy,45", "overcast,25", "rain,20", "sunny,10"),
                75, 4, 60);
        chengdu.monthPatterns.put(Month.JANUARY,   new SeasonPattern(10, 3, weightedMap("cloudy,40", "overcast,35", "rain,15", "sunny,5", "fog,5"), 75, 2, 90));
        chengdu.monthPatterns.put(Month.FEBRUARY,  new SeasonPattern(12, 5, weightedMap("cloudy,40", "overcast,30", "rain,15", "sunny,10", "fog,5"), 75, 3, 85));
        chengdu.monthPatterns.put(Month.MARCH,     new SeasonPattern(17, 9, weightedMap("cloudy,40", "overcast,30", "rain,20", "sunny,10"), 75, 4, 75));
        chengdu.monthPatterns.put(Month.APRIL,     new SeasonPattern(23, 14,weightedMap("cloudy,40", "overcast,25", "rain,20", "sunny,15"), 75, 5, 60));
        chengdu.monthPatterns.put(Month.MAY,       new SeasonPattern(27, 18,weightedMap("cloudy,40", "overcast,25", "rain,25", "sunny,10"), 78, 7, 55));
        chengdu.monthPatterns.put(Month.JUNE,      new SeasonPattern(29, 21,weightedMap("cloudy,35", "overcast,30", "rain,25", "sunny,10"), 80, 7, 50));
        chengdu.monthPatterns.put(Month.JULY,      new SeasonPattern(31, 23,weightedMap("cloudy,35", "overcast,25", "rain,30", "sunny,5", "heavy_rain,5"), 82, 8, 45));
        chengdu.monthPatterns.put(Month.AUGUST,    new SeasonPattern(31, 23,weightedMap("cloudy,35", "overcast,25", "rain,30", "sunny,5", "heavy_rain,5"), 82, 8, 45));
        chengdu.monthPatterns.put(Month.SEPTEMBER, new SeasonPattern(26, 19,weightedMap("cloudy,45", "overcast,25", "rain,25", "sunny,5"), 80, 5, 50));
        chengdu.monthPatterns.put(Month.OCTOBER,   new SeasonPattern(21, 15,weightedMap("cloudy,45", "overcast,30", "rain,15", "sunny,10"), 78, 4, 55));
        chengdu.monthPatterns.put(Month.NOVEMBER,  new SeasonPattern(16, 10,weightedMap("cloudy,45", "overcast,30", "rain,15", "sunny,10"), 78, 3, 70));
        chengdu.monthPatterns.put(Month.DECEMBER,  new SeasonPattern(11, 4, weightedMap("cloudy,45", "overcast,30", "rain,15", "sunny,5", "fog,5"), 75, 2, 85));
        chengdu.windDirections = weightedDirList("北,40", "东北,30", "东,20", "南,10");
        chengdu.windSpeeds = weightedSpeedList("1级,40", "2级,50", "3级,10");
        CLIMATES.put("成都", chengdu);

        // --- Xi'an ---
        CityClimate xian = new CityClimate();
        xian.defaultPattern = new SeasonPattern(22, 10,
                weightedMap("sunny,35", "cloudy,30", "overcast,20", "rain,15"),
                55, 6, 70);
        xian.monthPatterns.put(Month.JANUARY,   new SeasonPattern(6, -4, weightedMap("sunny,45", "cloudy,30", "overcast,15", "snow,10"), 45, 3, 110));
        xian.monthPatterns.put(Month.FEBRUARY,  new SeasonPattern(10, 0, weightedMap("sunny,40", "cloudy,30", "overcast,15", "rain,10", "snow,5"), 45, 4, 100));
        xian.monthPatterns.put(Month.MARCH,     new SeasonPattern(15, 5, weightedMap("sunny,35", "cloudy,30", "overcast,20", "rain,15"), 50, 5, 90));
        xian.monthPatterns.put(Month.APRIL,     new SeasonPattern(22, 11,weightedMap("sunny,35", "cloudy,30", "overcast,20", "rain,15"), 55, 6, 75));
        xian.monthPatterns.put(Month.MAY,       new SeasonPattern(27, 16,weightedMap("sunny,40", "cloudy,25", "overcast,20", "rain,15"), 55, 8, 65));
        xian.monthPatterns.put(Month.JUNE,      new SeasonPattern(33, 20,weightedMap("sunny,35", "cloudy,25", "overcast,20", "rain,20"), 60, 9, 55));
        xian.monthPatterns.put(Month.JULY,      new SeasonPattern(33, 23,weightedMap("sunny,25", "cloudy,25", "overcast,20", "rain,20", "heavy_rain,10"), 70, 10, 50));
        xian.monthPatterns.put(Month.AUGUST,    new SeasonPattern(32, 22,weightedMap("sunny,25", "cloudy,25", "overcast,25", "rain,25"), 70, 9, 50));
        xian.monthPatterns.put(Month.SEPTEMBER, new SeasonPattern(26, 16,weightedMap("sunny,30", "cloudy,30", "overcast,20", "rain,20"), 65, 6, 55));
        xian.monthPatterns.put(Month.OCTOBER,   new SeasonPattern(20, 10,weightedMap("sunny,40", "cloudy,30", "overcast,20", "rain,10"), 55, 4, 65));
        xian.monthPatterns.put(Month.NOVEMBER,  new SeasonPattern(12, 2, weightedMap("sunny,40", "cloudy,30", "overcast,20", "rain,10"), 50, 3, 80));
        xian.monthPatterns.put(Month.DECEMBER,  new SeasonPattern(7, -3, weightedMap("sunny,45", "cloudy,30", "overcast,15", "snow,10"), 45, 2, 105));
        xian.windDirections = weightedDirList("东北,40", "北,30", "东,20", "南,10");
        xian.windSpeeds = weightedSpeedList("2级,35", "3级,50", "4级,15");
        CLIMATES.put("西安", xian);

        // --- Dali ---
        CityClimate dali = new CityClimate();
        dali.defaultPattern = new SeasonPattern(22, 10,
                weightedMap("sunny,45", "cloudy,30", "overcast,15", "rain,10"),
                50, 8, 30);
        dali.monthPatterns.put(Month.JANUARY,   new SeasonPattern(16, 3, weightedMap("sunny,60", "cloudy,30", "overcast,10"), 40, 7, 25));
        dali.monthPatterns.put(Month.FEBRUARY,  new SeasonPattern(18, 5, weightedMap("sunny,55", "cloudy,30", "overcast,15"), 40, 8, 28));
        dali.monthPatterns.put(Month.MARCH,     new SeasonPattern(21, 8, weightedMap("sunny,50", "cloudy,30", "overcast,15", "rain,5"), 45, 9, 30));
        dali.monthPatterns.put(Month.APRIL,     new SeasonPattern(24, 11,weightedMap("sunny,45", "cloudy,30", "overcast,15", "rain,10"), 50, 9, 30));
        dali.monthPatterns.put(Month.MAY,       new SeasonPattern(26, 14,weightedMap("sunny,35", "cloudy,30", "overcast,20", "rain,15"), 55, 10, 28));
        dali.monthPatterns.put(Month.JUNE,      new SeasonPattern(26, 16,weightedMap("cloudy,25", "overcast,25", "rain,35", "sunny,15"), 70, 8, 25));
        dali.monthPatterns.put(Month.JULY,      new SeasonPattern(26, 17,weightedMap("rain,35", "cloudy,25", "overcast,25", "sunny,15"), 75, 8, 22));
        dali.monthPatterns.put(Month.AUGUST,    new SeasonPattern(26, 17,weightedMap("rain,35", "cloudy,25", "overcast,25", "sunny,15"), 75, 8, 22));
        dali.monthPatterns.put(Month.SEPTEMBER, new SeasonPattern(25, 15,weightedMap("sunny,25", "cloudy,30", "overcast,25", "rain,20"), 70, 8, 25));
        dali.monthPatterns.put(Month.OCTOBER,   new SeasonPattern(23, 12,weightedMap("sunny,45", "cloudy,30", "overcast,15", "rain,10"), 55, 7, 28));
        dali.monthPatterns.put(Month.NOVEMBER,  new SeasonPattern(19, 7, weightedMap("sunny,55", "cloudy,30", "overcast,15"), 45, 7, 28));
        dali.monthPatterns.put(Month.DECEMBER,  new SeasonPattern(17, 4, weightedMap("sunny,60", "cloudy,30", "overcast,10"), 40, 6, 25));
        dali.windDirections = weightedDirList("西南,35", "南,30", "西,20", "东南,15");
        dali.windSpeeds = weightedSpeedList("2级,30", "3级,55", "4级,15");
        CLIMATES.put("大理", dali);

        // --- Sanya ---
        CityClimate sanya = new CityClimate();
        sanya.defaultPattern = new SeasonPattern(30, 24,
                weightedMap("sunny,45", "cloudy,30", "rain,15", "heavy_rain,5", "storm,5"),
                75, 9, 25);
        sanya.monthPatterns.put(Month.JANUARY,   new SeasonPattern(26, 20,weightedMap("sunny,55", "cloudy,30", "rain,15"), 70, 7, 25));
        sanya.monthPatterns.put(Month.FEBRUARY,  new SeasonPattern(27, 21,weightedMap("sunny,50", "cloudy,30", "rain,20"), 72, 8, 25));
        sanya.monthPatterns.put(Month.MARCH,     new SeasonPattern(29, 23,weightedMap("sunny,50", "cloudy,30", "rain,20"), 75, 9, 25));
        sanya.monthPatterns.put(Month.APRIL,     new SeasonPattern(31, 25,weightedMap("sunny,45", "cloudy,30", "rain,25"), 78, 10, 25));
        sanya.monthPatterns.put(Month.MAY,       new SeasonPattern(32, 26,weightedMap("sunny,35", "cloudy,25", "rain,25", "heavy_rain,10", "storm,5"), 80, 11, 22));
        sanya.monthPatterns.put(Month.JUNE,      new SeasonPattern(33, 27,weightedMap("sunny,30", "cloudy,25", "rain,25", "heavy_rain,15", "storm,5"), 82, 11, 20));
        sanya.monthPatterns.put(Month.JULY,      new SeasonPattern(33, 27,weightedMap("sunny,30", "cloudy,25", "rain,25", "heavy_rain,15", "storm,5"), 82, 11, 20));
        sanya.monthPatterns.put(Month.AUGUST,    new SeasonPattern(33, 27,weightedMap("sunny,25", "cloudy,25", "rain,25", "heavy_rain,15", "storm,10"), 82, 11, 20));
        sanya.monthPatterns.put(Month.SEPTEMBER, new SeasonPattern(32, 26,weightedMap("sunny,30", "cloudy,25", "rain,25", "heavy_rain,15", "storm,5"), 80, 10, 22));
        sanya.monthPatterns.put(Month.OCTOBER,   new SeasonPattern(30, 24,weightedMap("sunny,45", "cloudy,30", "rain,25"), 75, 9, 25));
        sanya.monthPatterns.put(Month.NOVEMBER,  new SeasonPattern(28, 22,weightedMap("sunny,50", "cloudy,30", "rain,20"), 72, 8, 25));
        sanya.monthPatterns.put(Month.DECEMBER,  new SeasonPattern(26, 20,weightedMap("sunny,55", "cloudy,30", "rain,15"), 70, 7, 25));
        sanya.windDirections = weightedDirList("东南,40", "南,30", "东,20", "西南,10");
        sanya.windSpeeds = weightedSpeedList("2级,25", "3级,50", "4级,25");
        CLIMATES.put("三亚", sanya);
    }

    // ---- internal structures ----

    private static class CityClimate {
        SeasonPattern defaultPattern;
        Map<Month, SeasonPattern> monthPatterns = new EnumMap<>(Month.class);
        Map<String, Integer> windDirections;    // direction -> weight
        Map<String, Integer> windSpeeds;        // speed -> weight
    }

    private static class SeasonPattern {
        int baseHigh;
        int baseLow;
        Map<String, Integer> conditions;  // code -> weight
        int baseHumidity;
        int baseUv;
        int baseAqi;

        SeasonPattern(int baseHigh, int baseLow, Map<String, Integer> conditions,
                      int baseHumidity, int baseUv, int baseAqi) {
            this.baseHigh = baseHigh;
            this.baseLow = baseLow;
            this.conditions = conditions;
            this.baseHumidity = baseHumidity;
            this.baseUv = baseUv;
            this.baseAqi = baseAqi;
        }
    }

    // ---- factory helpers for compact data definition ----

    private static Map<String, Integer> weightedMap(String... entries) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String e : entries) {
            String[] parts = e.split(",");
            map.put(parts[0], Integer.parseInt(parts[1]));
        }
        return map;
    }

    private static Map<String, Integer> weightedDirList(String... entries) {
        return weightedMap(entries);
    }

    private static Map<String, Integer> weightedSpeedList(String... entries) {
        return weightedMap(entries);
    }
}
