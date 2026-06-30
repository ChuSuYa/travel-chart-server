package com.travelchart.common.interceptor;

import com.travelchart.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> IGNORE_PATHS = Arrays.asList(
            "/api/user/sms-code",
            "/api/user/login/**",
            "/api/user/register",
            "/api/home/**"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();

        // Skip ignored paths
        if (IGNORE_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return true;
        }

        // OPTIONS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            return false;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(401);
            return false;
        }

        Long userId = jwtUtil.getUserId(token);
        request.setAttribute("userId", userId);
        return true;
    }
}
