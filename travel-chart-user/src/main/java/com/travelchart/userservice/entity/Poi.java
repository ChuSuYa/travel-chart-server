package com.travelchart.userservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tg_poi")
public class Poi {

    @TableId(value = "poi_id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String city;

    private Double lat;

    @TableField("lng")
    private Double lon;

    private String tags;

    @TableField(exist = false)
    private Double price;

    private Double rating;

    @TableField(exist = false)
    private Double heatScore;

    @TableField(exist = false)
    private String imageUrl;

    private LocalDateTime createTime;
}
