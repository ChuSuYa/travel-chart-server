package com.travelchart.userservice.client;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Feign 传输用的 POI 文档 DTO（与 travel-chart-search 的 PoiDocument 对应）
 */
@Data
public class PoiDocument {

    private Long id;
    private String name;
    private String description;
    private String city;
    private Double lat;
    private Double lon;
    private List<String> tags;
    private Double price;
    private Double rating;
    private Double heatScore;
    private String imageUrl;
    private Date createTime;
}
