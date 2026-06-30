package com.travelchart.planservice.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travelchart.planservice.dto.GeneratePlanRequest;
import com.travelchart.planservice.dto.PlanDTO;
import com.travelchart.planservice.entity.TravelPlan;
import com.travelchart.planservice.mapper.PlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanMapper planMapper;

    /**
     * AI生成行程方案
     */
    public TravelPlan generatePlan(Long userId, GeneratePlanRequest request) {
        TravelPlan plan = new TravelPlan();
        plan.setUserId(userId);
        plan.setDestination(String.join(", ", request.getDestinations()));
        plan.setTitle(request.getDestinations().get(0) + "行程");
        plan.setStatus("planning");
        plan.setStartDate(request.getDateRange() != null && request.getDateRange().size() > 0
                ? request.getDateRange().get(0) : null);
        plan.setEndDate(request.getDateRange() != null && request.getDateRange().size() > 1
                ? request.getDateRange().get(1) : null);
        plan.setTotalBudget(request.getBudget() != null
                ? Double.valueOf(request.getBudget().getAmount()) : null);

        // 调用AI引擎生成行程方案（此处为模拟数据）
        String generatedContent = generateAIPlan(request);
        plan.setContent(generatedContent);

        planMapper.insert(plan);
        return plan;
    }

    /**
     * AI行程生成核心逻辑（模拟）
     */
    private String generateAIPlan(GeneratePlanRequest request) {
        // 实际应调用AI大模型API + POI检索 + 约束求解
        // 此处返回模拟的行程JSON数据
        String dest = request.getDestinations() != null && !request.getDestinations().isEmpty()
                ? request.getDestinations().get(0) : "未知目的地";
        String themes = request.getThemes() != null ? request.getThemes().toString() : "[]";
        String pace = request.getPace() != null ? request.getPace() : "relaxed";
        return """
            {
                "destination": "%s",
                "themes": %s,
                "pace": "%s",
                "days": [
                    {
                        "date": "Day 1",
                        "items": [
                            {"time": "09:00", "name": "%s地标打卡", "category": "景点", "duration": "2h"},
                            {"time": "12:00", "name": "当地美食午餐", "category": "餐饮", "cost": 100},
                            {"time": "14:00", "name": "博物馆参观", "category": "文化", "duration": "2h"}
                        ]
                    }
                ]
            }
            """.formatted(dest, themes, pace, dest);
    }

    public TravelPlan getPlanDetail(Long planId) {
        TravelPlan plan = planMapper.selectById(planId);
        if (plan == null) throw new RuntimeException("行程不存在");
        return plan;
    }

    public TravelPlan regeneratePlan(Long userId, Long planId, GeneratePlanRequest request) {
        TravelPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new RuntimeException("行程不存在或无权限");
        }
        String newContent = generateAIPlan(request);
        plan.setContent(newContent);
        planMapper.updateById(plan);
        return plan;
    }

    public List<PlanDTO> getPlanList(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<TravelPlan> qw = new LambdaQueryWrapper<>();
        qw.eq(TravelPlan::getUserId, userId).orderByDesc(TravelPlan::getCreateTime);
        Page<TravelPlan> pageResult = planMapper.selectPage(new Page<>(page, size), qw);
        return pageResult.getRecords().stream()
                .map(p -> BeanUtil.copyProperties(p, PlanDTO.class))
                .collect(Collectors.toList());
    }

    public void savePlan(Long userId, Long planId) {
        TravelPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new RuntimeException("行程不存在或无权限");
        }
        plan.setStatus("planning");
        planMapper.updateById(plan);
    }

    public void deletePlan(Long userId, Long planId) {
        TravelPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new RuntimeException("行程不存在或无权限");
        }
        planMapper.deleteById(planId);
    }

    public TravelPlan clonePlan(Long userId, Long planId) {
        TravelPlan plan = planMapper.selectById(planId);
        if (plan == null) throw new RuntimeException("行程不存在");
        TravelPlan cloned = new TravelPlan();
        BeanUtil.copyProperties(plan, cloned, "id", "createTime", "updateTime");
        cloned.setUserId(userId);
        cloned.setTitle(plan.getTitle() + "(副本)");
        cloned.setStatus("planning");
        cloned.setShareCount(0);
        planMapper.insert(cloned);
        return cloned;
    }

    public String getPlanWeather(Long planId) {
        TravelPlan plan = planMapper.selectById(planId);
        if (plan == null) throw new RuntimeException("行程不存在");
        // 返回关联的天气数据（这里模拟返回）
        return """
            {
                "climateSummary": "22-30\u2103, 多云为主",
                "dailyForecast": []
            }
            """;
    }
}
