package com.travelchart.userservice.controller;

import com.travelchart.common.result.Result;
import com.travelchart.common.util.JwtUtil;
import com.travelchart.userservice.dto.*;
import com.travelchart.userservice.entity.User;
import com.travelchart.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

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
    public Result<Map<String, Object>> refreshToken(@Valid @RequestBody RefreshDTO dto) {
        TokenDTO tokenDTO = userService.refreshToken(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", tokenDTO.getAccessToken());
        result.put("expiresIn", 7200);
        return Result.success(result);
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@Valid @RequestBody RefreshDTO dto) {
        TokenDTO tokenDTO = userService.refreshToken(dto);
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", tokenDTO.getAccessToken());
        result.put("expiresIn", 7200);
        return Result.success(result);
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

    // ========== 退出登录 ==========
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody LogoutDTO dto) {
        userService.logout(dto);
        return Result.success();
    }

    // ========== 主题偏好 ==========
    @PutMapping("/theme")
    public Result<Void> updateTheme(@RequestHeader("X-User-Id") Long userId, @RequestParam String mode) {
        userService.updateTheme(userId, mode);
        return Result.success();
    }
}
