package com.travelchart.userservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tg_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long userId;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String themeMode;
    private String language;
    private Integer inspiration;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
