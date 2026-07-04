package com.travelchart.gateway.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Value("${jwt.secret}")
    private String secret;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> WHITE_LIST = Arrays.asList(
        "/api/user/sms",
        "/api/user/register",
        "/api/user/login/code",
        "/api/user/login/password",
        "/api/user/login/sms",
        "/api/user/login/wechat",
        "/api/user/refresh",
        "/api/user/token/refresh",
        "/api/user/sms-code",
        "/api/user/theme",
        "/api/user/language",
        "/api/home",
        "/api/home/",
        "/api/discover/recommendations",
        "/api/discover/insights",
        "/images"
    );

    public JwtAuthFilter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unAuthorized(exchange, "缺少认证令牌");
        }

        String token = authHeader.substring(7);

        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tokenType = (String) claims.get("type");

            if (!"access".equals(tokenType)) {
                return unAuthorized(exchange, "非法的token类型，请使用accessToken");
            }

            String blackKey = "token:blacklist:" + token;
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blackKey))) {
                return unAuthorized(exchange, "token已被注销");
            }

            String subject = claims.getSubject();
            if (subject == null || !subject.matches("\\d+")) {
                return unAuthorized(exchange, "token格式异常");
            }
            Long userId = Long.valueOf(subject);
            String phone = String.valueOf(claims.get("userId"));

            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Phone", phone != null && !phone.equals("null") ? phone : "")
                    .build();

            return chain.filter(exchange.mutate().request(request).build());

        } catch (Exception e) {
            log.warn("Auth failed: {}", e.getMessage());
            return unAuthorized(exchange, "token无效或已过期");
        }
    }

    private Mono<Void> unAuthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("code", 401);
            body.put("message", message);
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
