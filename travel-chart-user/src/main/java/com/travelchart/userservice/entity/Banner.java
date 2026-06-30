package com.travelchart.userservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tg_banner")
public class Banner {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String cityName;
    private String imageUrl;
    private String title;
    private String subtitle;
    private String linkType;
    private String linkValue;
    private Double heatScore;
    private Double baseScore;
    private Long clickCount;
    private Long viewCount;
    private Long favoriteCount;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
