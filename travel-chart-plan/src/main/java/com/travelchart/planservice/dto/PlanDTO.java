package com.travelchart.planservice.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PlanDTO {
    private Long id;
    private Long userId;
    private String destination;
    private String title;
    private String status;
    private String content;
    private String startDate;
    private String endDate;
    private Integer totalDays;
    private Double totalBudget;
    private String coverImage;
    private Integer shareCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
