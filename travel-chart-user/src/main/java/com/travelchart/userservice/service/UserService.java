package com.travelchart.userservice.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelchart.common.util.JwtUtil;
import com.travelchart.userservice.dto.*;
import com.travelchart.userservice.entity.Traveler;
import com.travelchart.userservice.entity.User;
import com.travelchart.userservice.mapper.TravelerMapper;
import com.travelchart.userservice.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final TravelerMapper travelerMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    public void sendSmsCode(String phone) {
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(
            SMS_CODE_PREFIX + phone, code, 5, TimeUnit.MINUTES
        );
        log.info("SMS code for {}: {}", phone, code);
    }

    @Transactional
    public TokenDTO register(RegisterDTO dto) {
        String cachedCode = stringRedisTemplate.opsForValue().get(SMS_CODE_PREFIX + dto.getPhone());
        if (cachedCode == null || !cachedCode.equals(dto.getCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        User exist = userMapper.selectByPhone(dto.getPhone());
        if (exist != null) {
            throw new RuntimeException("该手机号已注册");
        }

        User user = new User();
        user.setPhone(dto.getPhone());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPasswordHash(BCrypt.hashpw(dto.getPassword()));
        }
        user.setStatus(1);
        user.setInspiration(0);
        user.setThemeMode("system");
        user.setLanguage("zh-CN");
        userMapper.insert(user);

        stringRedisTemplate.delete(SMS_CODE_PREFIX + dto.getPhone());

        return buildTokenDTO(user);
    }

    public TokenDTO loginByCode(LoginDTO dto) {
        String cachedCode = stringRedisTemplate.opsForValue().get(SMS_CODE_PREFIX + dto.getPhone());
        if (cachedCode == null || !cachedCode.equals(dto.getCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        User user = userMapper.selectByPhone(dto.getPhone());
        boolean isNewUser = false;
        if (user == null) {
            user = new User();
            user.setPhone(dto.getPhone());
            user.setNickname("旅行者" + RandomUtil.randomNumbers(6));
            user.setStatus(1);
            user.setInspiration(0);
            user.setThemeMode("system");
            user.setLanguage("zh-CN");
            userMapper.insert(user);
            isNewUser = true;
        }

        stringRedisTemplate.delete(SMS_CODE_PREFIX + dto.getPhone());
        return buildTokenDTO(user, isNewUser);
    }

    public TokenDTO wechatLogin(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("微信授权码不能为空");
        }

        String openId = "wx_dev_" + code;
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getPhone, openId)
        );
        boolean isNewUser = false;
        if (user == null) {
            user = new User();
            user.setPhone(openId);
            user.setNickname("旅行者" + RandomUtil.randomNumbers(6));
            user.setStatus(1);
            user.setInspiration(0);
            user.setThemeMode("system");
            user.setLanguage("zh-CN");
            userMapper.insert(user);
            isNewUser = true;
        }

        return buildTokenDTO(user, isNewUser);
    }

    public TokenDTO loginByPassword(LoginDTO dto) {
        User user = userMapper.selectByPhone(dto.getPhone());
        if (user == null) {
            throw new RuntimeException("手机号未注册");
        }
        if (user.getPasswordHash() == null || !BCrypt.checkpw(dto.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("密码错误");
        }
        return buildTokenDTO(user);
    }

    public TokenDTO refreshToken(RefreshDTO dto) {
        if (!jwtUtil.validateToken(dto.getRefreshToken())) {
            throw new RuntimeException("refreshToken无效或已过期");
        }
        if (!jwtUtil.isRefreshToken(dto.getRefreshToken())) {
            throw new RuntimeException("非法的token类型");
        }

        String blackKey = TOKEN_BLACKLIST_PREFIX + dto.getRefreshToken();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blackKey))) {
            throw new RuntimeException("refreshToken已被注销");
        }

        Long userId = jwtUtil.getUserId(dto.getRefreshToken());
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new RuntimeException("用户不存在或已禁用");
        }

        stringRedisTemplate.opsForValue().set(
            blackKey, "1", jwtUtil.parseToken(dto.getRefreshToken()).getExpiration().getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS
        );

        return buildTokenDTO(user);
    }

    public void logout(LogoutDTO dto) {
        if (jwtUtil.validateToken(dto.getToken())) {
            var claims = jwtUtil.parseToken(dto.getToken());
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining > 0) {
                stringRedisTemplate.opsForValue().set(
                    TOKEN_BLACKLIST_PREFIX + dto.getToken(), "1", remaining, TimeUnit.MILLISECONDS
                );
            }
        }
    }

    public boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token));
    }

    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    public void updateTheme(Long userId, String themeMode) {
        User user = new User();
        user.setUserId(userId);
        user.setThemeMode(themeMode);
        userMapper.updateById(user);
    }

    public List<Traveler> listTravelers(Long userId) {
        return travelerMapper.selectByUserId(userId);
    }

    @Transactional
    public Traveler addTraveler(Long userId, TravelerDTO dto) {
        Traveler traveler = new Traveler();
        traveler.setUserId(userId);
        traveler.setName(dto.getName());
        traveler.setAge(dto.getAge() != null ? dto.getAge() : 0);
        traveler.setType(dto.getType() != null ? dto.getType() : "adult");
        traveler.setRelationTag(dto.getRelationTag());
        travelerMapper.insert(traveler);
        return traveler;
    }

    @Transactional
    public void removeTraveler(Long userId, Long travelerId) {
        LambdaQueryWrapper<Traveler> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Traveler::getId, travelerId)
               .eq(Traveler::getUserId, userId);
        travelerMapper.delete(wrapper);
    }

    @Transactional
    public Traveler updateTraveler(Long userId, TravelerDTO dto) {
        LambdaQueryWrapper<Traveler> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Traveler::getId, dto.getId())
               .eq(Traveler::getUserId, userId);
        Traveler traveler = travelerMapper.selectOne(wrapper);
        if (traveler == null) {
            throw new RuntimeException("出行人不存在");
        }
        traveler.setName(dto.getName());
        traveler.setAge(dto.getAge() != null ? dto.getAge() : traveler.getAge());
        traveler.setType(dto.getType() != null ? dto.getType() : traveler.getType());
        traveler.setRelationTag(dto.getRelationTag());
        travelerMapper.updateById(traveler);
        return traveler;
    }

    private TokenDTO buildTokenDTO(User user) {
        return buildTokenDTO(user, false);
    }

    private TokenDTO buildTokenDTO(User user, boolean isNewUser) {
        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getPhone());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());
        TokenDTO dto = new TokenDTO();
        dto.setAccessToken(accessToken);
        dto.setRefreshToken(refreshToken);
        dto.setExpiresIn(7200L);
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickname() != null ? user.getNickname() : "旅行者");
        dto.setNewUser(isNewUser);
        return dto;
    }
}
