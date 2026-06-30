package com.travelchart.userservice.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BannerVO {
    private Long id;
    private String cityName;
    private String imageUrl;
    private String title;
    private String subtitle;
    private String linkType;
    private String linkValue;
}
