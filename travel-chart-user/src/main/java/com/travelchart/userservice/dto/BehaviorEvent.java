package com.travelchart.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEvent {

    private String type;     // click / view / fav / share
    private Long poiId;
    private Long userId;
    private Long timestamp;
}
