package com.travelchart.socialservice.controller;

import com.travelchart.socialservice.dto.Result;
import com.travelchart.socialservice.entity.CompanionRequest;
import com.travelchart.socialservice.service.SocialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "社交服务", description = "分享卡片、求同行、灵感值")
@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @Operation(summary = "生成分享卡片")
    @PostMapping("/card")
    public Result<Map<String, Object>> generateCard(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam Long planId,
            @RequestParam(defaultValue = "long") String template) {
        return Result.success(socialService.generateCard(userId, planId, template));
    }

    @Operation(summary = "分享回调")
    @PostMapping("/share-callback")
    public Result<Void> shareCallback(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam Long planId,
            @RequestParam String channel) {
        socialService.shareCallback(userId, planId, channel);
        return Result.success();
    }

    @Operation(summary = "发布求同行")
    @PostMapping("/companion")
    public Result<CompanionRequest> publishCompanion(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Object> params) {
        return Result.success(socialService.publishCompanion(userId, params));
    }

    @Operation(summary = "获取求同行列表")
    @GetMapping("/companion/list")
    public Result<List<CompanionRequest>> getCompanionList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(socialService.getCompanionList(page, size));
    }

    @Operation(summary = "获取灵感值")
    @GetMapping("/inspiration")
    public Result<Map<String, Object>> getInspiration(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(socialService.getInspiration(userId));
    }
}
