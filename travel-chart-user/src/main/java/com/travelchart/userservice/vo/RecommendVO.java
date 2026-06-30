package com.travelchart.userservice.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendVO {
    private Long id;
    private String name;
    private String city;
    private String imageUrl;
    private Double rating;
    private String price;
    private String tags;
}
