package com.travelchart.socialservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tg_companion_request")
public class CompanionRequest {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String destination;

    /** 行程日期范围 */
    private String dateRange;

    /** 预算 */
    private Double budget;

    /** 天气预期 */
    private String weatherExpectation;

    /** 期望旅伴画像 */
    private String companionProfile;

    /** 状态: active / closed */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
