package com.travelchart.weatherservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tg_reminder_log")
public class ReminderLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long userId;
    private String reminderType;
    private String title;
    private String content;
    private Integer isRead;
    private String pushStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
