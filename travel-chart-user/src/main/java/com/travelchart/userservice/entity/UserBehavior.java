package com.travelchart.userservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tg_user_behavior")
public class UserBehavior {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long poiId;
    private String actionType;
    private String targetId;
    private String metadata;
    private LocalDateTime eventTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
