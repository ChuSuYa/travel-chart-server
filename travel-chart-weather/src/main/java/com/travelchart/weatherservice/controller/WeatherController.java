package com.travelchart.weatherservice.controller;

import com.travelchart.common.model.Result;
import com.travelchart.weatherservice.entity.WeatherSnapshot;
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

    @GetMapping("/forecast")
    public Result<List<WeatherSnapshot>> getForecast(
            @RequestParam Long planId,
            @RequestParam String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(weatherService.getOrFetchWeather(planId, city, startDate, endDate));
    }

    @GetMapping("/luggage-checklist")
    public Result<Map<String, Object>> getLuggageChecklist(
            @RequestParam Long planId,
            @RequestParam String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(weatherService.getLuggageChecklist(planId, city, startDate, endDate));
    }
}
