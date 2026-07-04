package com.travelchart.userservice.controller;

import com.travelchart.common.result.Result;
import com.travelchart.common.util.JwtUtil;
import com.travelchart.userservice.dto.*;
import com.travelchart.userservice.entity.User;
import com.travelchart.userservice.service.InspirationService;
import com.travelchart.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final InspirationService inspirationService;

    // ========== 发送短信验证码 ==========
    @PostMapping("/sms-code")
    public Result<Void> sendSmsCode(@Valid @RequestBody SmsCodeDTO dto) {
        userService.sendSmsCode(dto.getPhone());
        return Result.success(null, "验证码已发送");
    }

    @PostMapping("/sms")
    public Result<Void> sendSms(@Valid @RequestBody SmsCodeDTO dto) {
        userService.sendSmsCode(dto.getPhone());
        return Result.success(null, "验证码已发送");
    }

    // ========== 短信验证码登录/注册 ==========
    @PostMapping("/login/sms")
    public Result<TokenDTO> loginBySms(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.loginByCode(dto));
    }

    @PostMapping("/login/code")
    public Result<TokenDTO> loginByCode(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.loginByCode(dto));
    }

    // ========== 密码登录 ==========
    @PostMapping("/login/password")
    public Result<TokenDTO> loginByPassword(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.loginByPassword(dto));
    }

    // ========== 微信登录 ==========
    @PostMapping("/login/wechat")
    public Result<TokenDTO> loginByWechat(@Valid @RequestBody WechatLoginDTO dto) {
        return Result.success(userService.wechatLogin(dto.getCode()));
    }

    // ========== 注册 ==========
    @PostMapping("/register")
    public Result<TokenDTO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    // ========== 刷新 Token ==========
    @PostMapping("/token/refresh")
    public Result<TokenDTO> refreshToken(@Valid @RequestBody RefreshDTO dto) {
        TokenDTO tokenDTO = userService.refreshToken(dto);
        return Result.success(tokenDTO);
    }

    @PostMapping("/refresh")
    public Result<TokenDTO> refresh(@Valid @RequestBody RefreshDTO dto) {
        TokenDTO tokenDTO = userService.refreshToken(dto);
        return Result.success(tokenDTO);
    }

    // ========== 获取用户信息 ==========
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile(@RequestHeader("Authorization") String auth) {
        String token = auth.replace("Bearer ", "");
        Long userId = jwtUtil.getUserId(token);
        User user = userService.getUserById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getUserId());
        profile.put("nickname", user.getNickname() != null ? user.getNickname() : "旅行者");
        profile.put("avatar", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        if (user.getPhone() != null && user.getPhone().length() >= 11) {
            profile.put("phone", user.getPhone().substring(0, 3) + "****" + user.getPhone().substring(7));
        } else {
            profile.put("phone", user.getPhone());
        }
        return Result.success(profile);
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(@RequestHeader("Authorization") String auth) {
        String token = auth.replace("Bearer ", "");
        Long userId = jwtUtil.getUserId(token);
        User user = userService.getUserById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getUserId());
        profile.put("nickname", user.getNickname() != null ? user.getNickname() : "旅行者");
        profile.put("avatar", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        profile.put("phone", user.getPhone());
        return Result.success(profile);
    }

    // ========== 退出登录 ==========
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody LogoutDTO dto) {
        userService.logout(dto);
        return Result.success();
    }

    // ========== 主题偏好 ==========
    @PutMapping("/theme")
    public Result<Void> updateTheme(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @RequestBody ThemeDTO dto) {
        Long userId = extractUserId(auth);
        userService.updateTheme(userId, dto.getThemeMode());
        return Result.success();
    }

    @GetMapping("/theme")
    public Result<Map<String, Object>> getTheme(@RequestHeader(value = "Authorization", required = false) String auth) {
        Long userId = extractUserId(auth);
        User user = userService.getUserById(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("themeMode", user != null && user.getThemeMode() != null ? user.getThemeMode() : "light");
        return Result.success(result);
    }

    // ========== 语言偏好 ==========
    @PutMapping("/language")
    public Result<Void> updateLanguage(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestBody Map<String, String> body) {
        Long userId = extractUserId(auth);
        String language = body.get("language");
        userService.updateLanguage(userId, language != null ? language : "zh-CN");
        return Result.success();
    }

    // ========== 灵感值 ==========
    @GetMapping("/inspiration")
    public Result<Map<String, Object>> getInspiration(@RequestHeader("Authorization") String auth) {
        Long userId = extractUserId(auth);
        int balance = inspirationService.getInspiration(userId);
        List<Map<String, Object>> history = inspirationService.getHistory(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("balance", balance);
        result.put("history", history);
        result.put("rules", buildInspirationRules());
        return Result.success(result);
    }

    private Map<String, String> buildInspirationRules() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("daily_login", "每日登录 +1");
        rules.put("complete_profile", "完善个人资料 +10");
        rules.put("generate_plan", "生成行程 +5");
        rules.put("share_plan", "分享行程 +15");
        rules.put("review_poi", "点评POI +3");
        return rules;
    }

    /** 从 JWT Token 中提取 userId；未登录返回默认 1（游客） */
    private Long extractUserId(String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                return jwtUtil.getUserId(auth.replace("Bearer ", ""));
            } catch (Exception ignored) {}
        }
        return 1L;
    }
}
