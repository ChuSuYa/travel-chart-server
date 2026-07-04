package com.travelchart.common.feign;

import com.travelchart.common.dto.WeatherSnapshotDTO;
import com.travelchart.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for the weather microservice.
 * Uses WeatherSnapshotDTO (in common) to avoid all modules depending on weather-service.
 */
@FeignClient(name = "travel-chart-weather")
public interface WeatherFeign {

    @GetMapping("/api/weather/forecast")
    Result<List<WeatherSnapshotDTO>> getForecast(@RequestParam Long planId, @RequestParam String city);
}
