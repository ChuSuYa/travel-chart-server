package com.travelchart.manageservice.service;

import cn.hutool.crypto.digest.BCrypt;
import com.travelchart.manageservice.entity.AdminUser;
import com.travelchart.manageservice.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {
    private final AdminUserMapper adminUserMapper;
    @Value("${admin.bootstrap.username:}") private String username;
    @Value("${admin.bootstrap.password:}") private String password;

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("No bootstrap administrator configured; set ADMIN_BOOTSTRAP_USERNAME and ADMIN_BOOTSTRAP_PASSWORD before first login.");
            return;
        }
        if (adminUserMapper.findByUsername(username) == null) {
            AdminUser user = new AdminUser();
            user.setUsername(username);
            user.setDisplayName(username);
            user.setPasswordHash(BCrypt.hashpw(password));
            user.setStatus(1);
            adminUserMapper.insert(user);
            log.info("Bootstrap administrator '{}' created.", username);
        }
    }
}
