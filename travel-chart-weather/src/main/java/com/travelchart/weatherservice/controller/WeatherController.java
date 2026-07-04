package com.travelchart.weatherservice.controller;

import com.travelchart.common.result.Result;
import com.travelchart.weatherservice.entity.WeatherSnapshot;
import com.travelchart.weatherservice.service.ReminderService;
import com.travelchart.weatherservice.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final ReminderService reminderService;

    @GetMapping("/forecast")
    public Result<List<WeatherSnapshot>> getForecast(
            @RequestParam Long planId,
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        // Default date range if not provided
        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusDays(7);
        return Result.ok(weatherService.getOrFetchWeather(planId, city, startDate, endDate));
    }

    @GetMapping("/luggage-checklist")
    public Result<Map<String, Object>> getLuggageChecklist(
            @RequestParam Long planId,
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusDays(7);
        return Result.ok(weatherService.getLuggageChecklist(planId, city, startDate, endDate));
    }

    /**
     * Returns a personalized daily motivation message based on current weather
     * for the specified plan's city.
     */
    @GetMapping("/daily-motivation")
    public Result<Map<String, String>> getDailyMotivation(
            @RequestParam Long planId,
            @RequestParam(required = false) String city) {
        // If city not provided, try to infer from weather records
        if (city == null || city.isEmpty()) {
            List<WeatherSnapshot> snapshots = weatherService.getOrFetchWeather(
                    planId, "北京", LocalDate.now(), LocalDate.now());
            if (snapshots != null && !snapshots.isEmpty()) {
                city = snapshots.get(0).getCity();
            } else {
                city = "北京";
            }
        }
        return Result.ok(reminderService.generateDailyMotivation(planId, city));
    }
}
