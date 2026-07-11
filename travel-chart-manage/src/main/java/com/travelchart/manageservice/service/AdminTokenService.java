package com.travelchart.manageservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class AdminTokenService {
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.access-token-expiration:7200}")
    private long expiration;

    public String createToken(Long adminId, String username) {
        return Jwts.builder().setSubject(String.valueOf(adminId))
                .claim("type", "access").claim("role", "ADMIN").claim("username", username)
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(key(), SignatureAlgorithm.HS256).compact();
    }

    public void requireAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new SecurityException("缺少管理员认证令牌");
        }
        Claims claims = Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(authorization.substring(7)).getPayload();
        if (!"access".equals(claims.get("type")) || !"ADMIN".equals(claims.get("role"))) {
            throw new SecurityException("管理员权限不足");
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
