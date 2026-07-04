package com.travelchart.planservice.controller;

import com.travelchart.common.dto.GeneratePlanRequest;
import com.travelchart.planservice.dto.PlanDTO;
import com.travelchart.common.result.Result;
import com.travelchart.planservice.entity.TravelPlan;
import com.travelchart.planservice.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "行程生成服务", description = "AI行程生成、检索、管理")
@RestController
@RequestMapping("/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @Operation(summary = "生成行程方案")
    @PostMapping("/generate")
    public Result<TravelPlan> generatePlan(@RequestHeader("X-User-Id") Long userId,
                                            @Valid @RequestBody GeneratePlanRequest request) {
        return Result.success(planService.generatePlan(userId, request));
    }

    @Operation(summary = "获取行程详情")
    @GetMapping("/{planId}")
    public Result<TravelPlan> getPlanDetail(@PathVariable Long planId) {
        return Result.success(planService.getPlanDetail(planId));
    }

    @Operation(summary = "重新生成行程")
    @PostMapping("/{planId}/regenerate")
    public Result<TravelPlan> regeneratePlan(@RequestHeader("X-User-Id") Long userId,
                                              @PathVariable Long planId,
                                              @RequestBody GeneratePlanRequest request) {
        return Result.success(planService.regeneratePlan(userId, planId, request));
    }

    @Operation(summary = "获取行程列表")
    @GetMapping("/list")
    public Result<List<PlanDTO>> getPlanList(@RequestHeader("X-User-Id") Long userId,
                                              @RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(planService.getPlanList(userId, page, size));
    }

    @Operation(summary = "保存行程")
    @PostMapping("/{planId}/save")
    public Result<Void> savePlan(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable Long planId) {
        planService.savePlan(userId, planId);
        return Result.success();
    }

    @Operation(summary = "删除行程")
    @DeleteMapping("/{planId}")
    public Result<Void> deletePlan(@RequestHeader("X-User-Id") Long userId,
                                    @PathVariable Long planId) {
        planService.deletePlan(userId, planId);
        return Result.success();
    }

    @Operation(summary = "克隆行程")
    @PostMapping("/{planId}/clone")
    public Result<TravelPlan> clonePlan(@RequestHeader("X-User-Id") Long userId,
                                         @PathVariable Long planId) {
        return Result.success(planService.clonePlan(userId, planId));
    }

    @Operation(summary = "获取行程关联天气")
    @GetMapping("/{planId}/weather")
    public Result<String> getPlanWeather(@PathVariable Long planId) {
        return Result.success(planService.getPlanWeather(planId));
    }
}
