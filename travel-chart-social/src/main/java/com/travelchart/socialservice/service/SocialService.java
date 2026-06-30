package com.travelchart.socialservice.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travelchart.socialservice.entity.CompanionRequest;
import com.travelchart.socialservice.entity.ShareCard;
import com.travelchart.socialservice.mapper.CompanionRequestMapper;
import com.travelchart.socialservice.mapper.ShareCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialService {

    private final ShareCardMapper shareCardMapper;
    private final CompanionRequestMapper companionRequestMapper;

    public Map<String, Object> generateCard(Long userId, Long planId, String template) {
        ShareCard card = new ShareCard();
        card.setUserId(userId);
        card.setPlanId(planId);
        card.setTemplate(template);

        Map<String, Object> cardContent = new LinkedHashMap<>();
        cardContent.put("planId", planId);
        cardContent.put("template", template);
        cardContent.put("generatedAt", System.currentTimeMillis());
        card.setCardContent(JSONUtil.toJsonStr(cardContent));

        shareCardMapper.insert(card);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cardId", card.getId());
        result.put("shareUrl", "https://travel-chart.app/share/" + card.getId());
        result.put("cardContent", cardContent);
        return result;
    }

    public void shareCallback(Long userId, Long planId, String channel) {
        log.info("User {} shared plan {} to {}", userId, planId, channel);
    }

    public CompanionRequest publishCompanion(Long userId, Map<String, Object> params) {
        CompanionRequest req = new CompanionRequest();
        req.setUserId(userId);
        req.setDestination((String) params.get("destination"));
        req.setDateRange((String) params.get("dateRange"));
        req.setBudget(params.get("budget") != null ? Double.valueOf(params.get("budget").toString()) : null);
        req.setWeatherExpectation((String) params.get("weatherExpectation"));
        req.setCompanionProfile((String) params.get("companionProfile"));
        req.setStatus("active");
        companionRequestMapper.insert(req);
        return req;
    }

    public List<CompanionRequest> getCompanionList(Integer page, Integer size) {
        LambdaQueryWrapper<CompanionRequest> qw = new LambdaQueryWrapper<>();
        qw.eq(CompanionRequest::getStatus, "active").orderByDesc(CompanionRequest::getCreateTime);
        return companionRequestMapper.selectPage(new Page<>(page, size), qw).getRecords();
    }

    public Map<String, Object> getInspiration(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", 88);
        result.put("history", Collections.emptyList());
        return result;
    }
}
