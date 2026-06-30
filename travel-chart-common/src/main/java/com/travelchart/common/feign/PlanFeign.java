package com.travelchart.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "plan-service")
public interface PlanFeign {
    @GetMapping("/api/plan/{planId}/weather")
    String getPlanWeather(@PathVariable Long planId);
}
