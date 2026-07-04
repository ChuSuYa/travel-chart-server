package com.travelchart.planservice.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.List;

/**
 * 行程引擎的最终输出结果。
 * 包含渲染后的每日行程数据。
 */
@Data
public class GeneratedPlan {

    /** 行程标题 */
    private String title;

    /** 目的地 */
    private String destination;

    /** 总预算 */
    private double totalBudget;

    /** 每日行程列表 */
    private List<DailyPlan> dailyPlans;

    /** 旅行主题列表 */
    private List<String> themes;

    /** 行程节奏（relaxed/compact/intensive） */
    private String pace;

    /** 详版行程 JSON */
    private String detailedVersion;

    /** 简版行程 JSON（用于偏好字段存储） */
    private String simpleVersion;

    /**
     * 将完整行程数据序列化为 JSON
     */
    public String toContentJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("行程序列化失败", e);
        }
    }

    /**
     * 从 JSON 反序列化
     */
    public static GeneratedPlan fromContentJson(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(json, GeneratedPlan.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("行程反序列化失败", e);
        }
    }
}
