package com.travelchart.userservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tg_recommend")
public class Recommend {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long poiId;
    private String name;
    private String city;
    private String imageUrl;
    private Double rating;
    private java.math.BigDecimal price;
    private String tags;
    private Double heatScore;
    private Double baseScore;
    private Long clickCount;
    private Long viewCount;
    private Long favoriteCount;
    private Long shareCount;
    private Long commentCount;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
