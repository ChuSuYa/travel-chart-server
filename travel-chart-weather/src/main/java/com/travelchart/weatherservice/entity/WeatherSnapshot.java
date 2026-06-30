package com.travelchart.weatherservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tg_weather_snapshot")
public class WeatherSnapshot {
    @TableId(type = IdType.AUTO)
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
