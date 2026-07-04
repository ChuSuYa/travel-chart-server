package com.travelchart.planservice.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travelchart.common.dto.GeneratePlanRequest;
import com.travelchart.planservice.dto.PlanDTO;
import com.travelchart.planservice.engine.*;
import com.travelchart.planservice.entity.TravelPlan;
import com.travelchart.planservice.mapper.PlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 行程服务 — 编排 5 阶段 AI 引擎流水线：
 *   意图解析 → 检索POI → 约束求解 → 路线优化 → 渲染输出
 *
 * 保留现有 CRUD 方法。渲染结果以 JSON 格式存入 plan.content。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanMapper planMapper;

    // ---- 引擎组件（Spring 注入） ----
    private final EngineConfig engineConfig;
    private final IntentParser intentParser;
    private final PoiRetriever poiRetriever;
    private final ConstraintSolver constraintSolver;
    private final RouteOptimizer routeOptimizer;
    private final PlanRenderer planRenderer;

    // ==================================================================
    //  AI 生成流水线
    // ==================================================================

    /**
     * 完整 5 阶段流水线：生成新行程
     */
    public TravelPlan generatePlan(Long userId, GeneratePlanRequest request) {
        // 阶段 1：解析意图
        IntentContext ctx = intentParser.parseFromRequest(request);
        log.info("阶段 1 - 意图已解析: {}", ctx);

        // 阶段 2：检索候选 POI
        List<PoiCandidate> candidates = poiRetriever.retrieve(ctx);
        log.info("阶段 2 - 已检索 {} 个候选, 共 {} 个 POI",
                candidates.size(), poiRetriever.totalPoiCount());

        // 阶段 3：求解硬约束 → 每日行程
        List<DailyPlan> dailyPlans = constraintSolver.solve(candidates, ctx);
        log.info("阶段 3 - 已求解 {} 天行程", dailyPlans.size());

        // 阶段 4：软约束优化
        dailyPlans = routeOptimizer.optimize(dailyPlans, ctx, candidates);
        log.info("阶段 4 - 已优化, 最高单日评分: {}",
                dailyPlans.isEmpty() ? "N/A" :
                        String.format("%.2f", routeOptimizer.scoreDay(dailyPlans.get(0), ctx, candidates)));

        // 阶段 5：渲染简版 + 详细版本
        GeneratedPlan generated = planRenderer.render(dailyPlans, ctx);
        log.info("阶段 5 - 行程已渲染: {} (预算 ~¥{})", generated.getTitle(), generated.getTotalBudget());

        // 持久化到数据库
        TravelPlan plan = new TravelPlan();
        plan.setUserId(userId);
        plan.setDestination(generated.getDestination());
        plan.setTitle(generated.getTitle());
        plan.setStatus("planning");
        plan.setStartDate(ctx.getDateRange() != null && !ctx.getDateRange().isEmpty()
                ? ctx.getDateRange().get(0) : null);
        plan.setEndDate(ctx.getDateRange() != null && ctx.getDateRange().size() > 1
                ? ctx.getDateRange().get(1) : null);
        plan.setTotalDays(dailyPlans.size());
        plan.setTotalBudget(generated.getTotalBudget());
        plan.setContent(generated.toContentJson());

        // preferences 字段中存储简版行程
        if (generated.getSimpleVersion() != null) {
            plan.setPreferences(generated.getSimpleVersion());
        }

        planMapper.insert(plan);
        log.info("行程已保存: id={}, title={}", plan.getId(), plan.getTitle());
        return plan;
    }

    /**
     * 基于已有行程重新生成（更新）
     */
    public TravelPlan regeneratePlan(Long userId, Long planId, GeneratePlanRequest request) {
        TravelPlan existing = planMapper.selectById(planId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("行程不存在或无权限");
        }

        // 执行完整流水线
        IntentContext ctx = intentParser.parseFromRequest(request);
        List<PoiCandidate> candidates = poiRetriever.retrieve(ctx);
        List<DailyPlan> dailyPlans = constraintSolver.solve(candidates, ctx);
        dailyPlans = routeOptimizer.optimize(dailyPlans, ctx, candidates);
        GeneratedPlan generated = planRenderer.render(dailyPlans, ctx);

        // 更新已有行程
        existing.setDestination(generated.getDestination());
        existing.setTitle(generated.getTitle());
        existing.setContent(generated.toContentJson());
        existing.setPreferences(generated.getSimpleVersion());
        existing.setTotalDays(dailyPlans.size());
        existing.setTotalBudget(generated.getTotalBudget());
        existing.setStartDate(ctx.getDateRange() != null && !ctx.getDateRange().isEmpty()
                ? ctx.getDateRange().get(0) : existing.getStartDate());
        existing.setEndDate(ctx.getDateRange() != null && ctx.getDateRange().size() > 1
                ? ctx.getDateRange().get(1) : existing.getEndDate());

        planMapper.updateById(existing);
        log.info("行程已重新生成: id={}, title={}", existing.getId(), generated.getTitle());
        return existing;
    }

    /**
     * 从自由文本生成行程（自然语言输入）
     */
    public TravelPlan generatePlanFromFreeText(Long userId, String freeText) {
        IntentContext ctx = intentParser.parseFromFreeText(freeText);
        log.info("自由文本意图已解析: {}", ctx);

        List<PoiCandidate> candidates = poiRetriever.retrieve(ctx);
        List<DailyPlan> dailyPlans = constraintSolver.solve(candidates, ctx);
        dailyPlans = routeOptimizer.optimize(dailyPlans, ctx, candidates);
        GeneratedPlan generated = planRenderer.render(dailyPlans, ctx);

        TravelPlan plan = new TravelPlan();
        plan.setUserId(userId);
        plan.setDestination(generated.getDestination());
        plan.setTitle(generated.getTitle());
        plan.setStatus("planning");
        plan.setTotalDays(dailyPlans.size());
        plan.setTotalBudget(generated.getTotalBudget());
        plan.setContent(generated.toContentJson());
        plan.setPreferences(generated.getSimpleVersion());

        planMapper.insert(plan);
        return plan;
    }

    // ==================================================================
    //  CRUD 方法
    // ==================================================================

    public TravelPlan getPlanDetail(Long planId) {
        TravelPlan plan = planMapper.selectById(planId);
        if (plan == null) throw new RuntimeException("行程不存在");
        return plan;
    }

    public List<PlanDTO> getPlanList(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<TravelPlan> qw = new LambdaQueryWrapper<>();
        qw.eq(TravelPlan::getUserId, userId).orderByDesc(TravelPlan::getCreateTime);
        Page<TravelPlan> pageResult = planMapper.selectPage(new Page<>(page, size), qw);
        return pageResult.getRecords().stream()
                .map(p -> {
                    PlanDTO dto = new PlanDTO();
                    BeanUtil.copyProperties(p, dto);
                    return dto;
                })
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

        // 从行程内容推断季节
        String season = "spring";
        if (plan.getStartDate() != null) {
            try {
                int month = java.time.LocalDate.parse(plan.getStartDate()).getMonthValue();
                if (month >= 3 && month <= 5) season = "spring";
                else if (month >= 6 && month <= 8) season = "summer";
                else if (month >= 9 && month <= 11) season = "autumn";
                else season = "winter";
            } catch (Exception ignored) {}
        }

        String climateSummary = switch (season) {
            case "spring" -> "温暖宜人 15-25°C，偶有小雨，建议携带薄外套";
            case "summer" -> "炎热 28-35°C，注意防晒防暑，午后可能有雷阵雨";
            case "autumn" -> "凉爽舒适 18-26°C，最佳旅行季节，天高云淡";
            case "winter" -> "寒冷干燥 0-10°C，注意保暖，室内景点优先";
            default -> "20-28°C，多云为主";
        };

        return String.format("""
            {
                "climateSummary": "%s",
                "season": "%s",
                "dailyForecast": [],
                "tips": "%s"
            }
            """, climateSummary, season, getSeasonalTip(season));
    }

    private String getSeasonalTip(String season) {
        return switch (season) {
            case "spring" -> "春季花粉较多，过敏体质建议备好口罩和抗过敏药";
            case "summer" -> "携带防晒霜、太阳镜、充足饮水，避开午间高温时段出行";
            case "autumn" -> "秋高气爽是徒步和摄影的最佳时节，建议早出晚归充分利用白天时间";
            case "winter" -> "室内景点不受天气影响，可搭配温泉行程提升舒适度";
            default -> "出行前请关注当地实时天气预报";
        };
    }
}
