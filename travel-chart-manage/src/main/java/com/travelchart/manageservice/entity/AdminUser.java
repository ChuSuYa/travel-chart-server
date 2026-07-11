package com.travelchart.manageservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tg_admin_user")
public class AdminUser {
    @TableId(value = "admin_id", type = IdType.AUTO)
    private Long adminId;
    private String username;
    private String passwordHash;
    private String displayName;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
