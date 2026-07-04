package com.travelchart.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * Shared weather snapshot DTO used across modules.
 * Used by WeatherFeign, SocialService, and any module that needs weather data without
 * depending on the weather-service module directly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherSnapshotDTO {
    private Long id;
    private Long planId;
    private String city;
    private LocalDate forecastDate;
    private Integer tempHigh;
    private Integer tempLow;
    private String conditionCode;
    private String conditionText;
    private Integer humidity;
    private Integer uvIndex;
    private Integer aqi;
    private String windDirection;
    private String windSpeed;
    private String alertLevel;
    private String alertText;
}
