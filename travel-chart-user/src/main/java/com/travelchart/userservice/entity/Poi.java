package com.travelchart.userservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tg_poi")
public class Poi {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String city;

    private Double lat;

    private Double lon;

    private String tags;

    private Double price;

    private Double rating;

    private Double heatScore;

    private String imageUrl;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
