package com.travelchart.manageservice.controller;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.travelchart.common.result.Result;
import com.travelchart.manageservice.dto.AdminLoginRequest;
import com.travelchart.manageservice.entity.AdminUser;
import com.travelchart.manageservice.mapper.AdminUserMapper;
import com.travelchart.manageservice.mapper.ManageMapper;
import com.travelchart.manageservice.service.AdminTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manage")
@RequiredArgsConstructor
public class ManageController {
    private final AdminUserMapper adminUserMapper;
    private final ManageMapper manageMapper;
    private final AdminTokenService tokenService;

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminUser admin = adminUserMapper.findByUsername(request.getUsername());
        if (admin == null || admin.getStatus() != 1 || !BCrypt.checkpw(request.getPassword(), admin.getPasswordHash())) {
            return Result.error(401, "用户名或密码错误");
        }
        adminUserMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .eq(AdminUser::getAdminId, admin.getAdminId()).set(AdminUser::getLastLoginTime, LocalDateTime.now()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", tokenService.createToken(admin.getAdminId(), admin.getUsername()));
        data.put("expiresIn", 7200);
        data.put("admin", Map.of("adminId", admin.getAdminId(), "username", admin.getUsername(), "displayName", admin.getDisplayName()));
        return Result.success(data);
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Long>> dashboard(@RequestHeader("Authorization") String authorization) {
        tokenService.requireAdmin(authorization);
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("totalUsers", manageMapper.countUsers());
        data.put("activeUsers", manageMapper.countActiveUsers());
        data.put("totalPlans", manageMapper.countPlans());
        data.put("publishedPois", manageMapper.countPublishedPois());
        return Result.success(data);
    }

    @GetMapping("/users")
    public Result<Map<String, Object>> users(@RequestHeader("Authorization") String authorization,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String keyword) {
        tokenService.requireAdmin(authorization);
        return Result.success(pageResult(page, size, keyword, false));
    }

    @PatchMapping("/users/{userId}/status")
    public Result<Void> updateUserStatus(@RequestHeader("Authorization") String authorization, @PathVariable long userId,
                                         @RequestParam int status) {
        tokenService.requireAdmin(authorization);
        checkStatus(status);
        if (manageMapper.updateUserStatus(userId, status) == 0) return Result.error(404, "用户不存在");
        return Result.success();
    }

    @GetMapping("/content/pois")
    public Result<Map<String, Object>> pois(@RequestHeader("Authorization") String authorization,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String keyword) {
        tokenService.requireAdmin(authorization);
        return Result.success(pageResult(page, size, keyword, true));
    }

    @PatchMapping("/content/pois/{poiId}/status")
    public Result<Void> updatePoiStatus(@RequestHeader("Authorization") String authorization, @PathVariable long poiId,
                                        @RequestParam int status) {
        tokenService.requireAdmin(authorization);
        checkStatus(status);
        if (manageMapper.updatePoiStatus(poiId, status) == 0) return Result.error(404, "POI不存在");
        return Result.success();
    }

    private Map<String, Object> pageResult(int page, int size, String keyword, boolean poi) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String query = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        long total = poi ? manageMapper.countPoisByKeyword(query) : manageMapper.countUsersByKeyword(query);
        List<Map<String, Object>> records = poi
                ? manageMapper.findPois(query, (long) (safePage - 1) * safeSize, safeSize)
                : manageMapper.findUsers(query, (long) (safePage - 1) * safeSize, safeSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("total", total);
        return data;
    }

    private void checkStatus(int status) {
        if (status != 0 && status != 1) throw new IllegalArgumentException("status 只能为 0 或 1");
    }
}
