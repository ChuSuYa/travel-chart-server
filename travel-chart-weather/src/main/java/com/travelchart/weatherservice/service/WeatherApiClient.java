package com.travelchart.weatherservice.service;

import java.time.LocalDate;
import java.util.List;

/**
 * External weather API abstraction.
 * Implementations fetch weather data from real APIs or generate mock data.
 */
public interface WeatherApiClient {

    /**
     * Fetch weather forecast for a city within the given date range.
     *
     * @param city      city name (Chinese)
     * @param startDate forecast start
     * @param endDate   forecast end
     * @return list of daily weather responses, one per day
     */
    List<WeatherResponse> fetch(String city, LocalDate startDate, LocalDate endDate);

    /**
     * A single day's weather data returned by the API.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class WeatherResponse {
        private String city;
        private LocalDate forecastDate;
        private int tempHigh;
        private int tempLow;
        private String conditionCode;
        private String conditionText;
        private int humidity;
        private int uvIndex;
        private int aqi;
        private String windDirection;
        private String windSpeed;
        private String alertLevel;
        private String alertText;
    }
}
