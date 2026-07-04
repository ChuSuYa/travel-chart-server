package com.travelchart.planservice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tg_plan")
public class TravelPlan {
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 目的地 */
    @TableField("destinations")
    private String destination;

    /** 行程标题 */
    private String title;

    /** 行程状态: planning / traveled / shared */
    private String status;

    /** 行程内容 JSON */
    private String content;

    /** 偏好配置 JSON */
    private String preferences;

    /** 开始日期 */
    private String startDate;

    /** 结束日期 */
    private String endDate;

    /** 总计天数 */
    @TableField("day_count")
    private Integer totalDays;

    /** 总预算 */
    private Double totalBudget;

    /** 封面图片URL */
    private String coverImage;

    /** 分享次数 */
    private Integer shareCount;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
