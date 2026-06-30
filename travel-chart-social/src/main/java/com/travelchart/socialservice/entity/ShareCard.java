package com.travelchart.socialservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tg_share_card")
public class ShareCard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long userId;
    private String cardImageUrl;
    private String cardData;
    private String template;
    private String cardContent;
    private Integer shareCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
