package com.travelchart.socialservice.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travelchart.common.dto.WeatherSnapshotDTO;
import com.travelchart.common.feign.WeatherFeign;
import com.travelchart.common.result.Result;
import com.travelchart.socialservice.entity.CompanionRequest;
import com.travelchart.socialservice.entity.ShareCard;
import com.travelchart.socialservice.mapper.CompanionRequestMapper;
import com.travelchart.socialservice.mapper.ShareCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Core social service: share cards, companion matching, inspiration tracking, and discovery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialService {

    private final ShareCardMapper shareCardMapper;
    private final CompanionRequestMapper companionRequestMapper;
    private final WeatherFeign weatherFeign;
    private final StringRedisTemplate stringRedisTemplate;

    // ================================================================
    //  Share card generation
    // ================================================================

    /**
     * Generate a share card with real data from the plan:
     * cover image, trip highlights, budget, weather, QR code, template.
     *
     * Template options: long-image, short-video, instagram-story, xiaohongshu-note
     */
    public Map<String, Object> generateCard(Long userId, Long planId, String template) {
        ShareCard card = new ShareCard();
        card.setUserId(userId);
        card.setPlanId(planId);
        card.setTemplate(template);
        card.setShareCount(0);

        Map<String, Object> cardContent = new LinkedHashMap<>();
        cardContent.put("planId", planId);
        cardContent.put("template", template);
        cardContent.put("generatedAt", System.currentTimeMillis());

        // 1. Fetch plan data via the plan Feign (plan info JSON stored in cardContent)
        //    Since we don't have a direct PlanFeign that returns full plan data yet,
        //    we construct meaningful content from the fields available.
        //    In production this would call: planFeign.getPlanById(planId)
        PlanData planData = resolvePlanData(planId, userId);

        // 2. Cover image
        String coverImage = planData.coverImage;
        if (!StringUtils.hasText(coverImage)) {
            coverImage = getDefaultCoverForDestination(planData.destination);
        }
        cardContent.put("coverImage", coverImage);
        cardContent.put("destination", planData.destination);
        cardContent.put("title", planData.title);

        // 3. Trip highlights (extracted from plan content JSON)
        List<String> highlights = extractHighlights(planData.content);
        cardContent.put("highlights", highlights);

        // 4. Budget summary
        Map<String, Object> budgetSummary = buildBudgetSummary(planData);
        cardContent.put("budgetSummary", budgetSummary);

        // 5. Weather summary (call WeatherFeign)
        Map<String, Object> weatherSummary = fetchWeatherSummary(planId, planData.destination);
        cardContent.put("weatherSummary", weatherSummary);

        // 6. QR code URL placeholder
        cardContent.put("qrCodeUrl", "https://travel-chart.app/share/qr/" + planId);

        // 7. Template-specific rendering hints
        cardContent.put("templateMeta", buildTemplateMeta(template));

        card.setCardContent(JSONUtil.toJsonStr(cardContent));
        card.setCardImageUrl(coverImage);

        shareCardMapper.insert(card);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cardId", card.getId());
        result.put("shareUrl", "https://travel-chart.app/share/" + card.getId());
        result.put("cardContent", cardContent);
        result.put("template", template);
        return result;
    }

    /**
     * Share callback: record the share event, increment counters.
     */
    public void shareCallback(Long userId, Long planId, String channel) {
        log.info("User {} shared plan {} to {}", userId, planId, channel);

        // Increment share_count on the share_card record
        shareCardMapper.update(null,
                new LambdaUpdateWrapper<ShareCard>()
                        .eq(ShareCard::getPlanId, planId)
                        .setSql("share_count = share_count + 1"));

        // Track share event in Redis for analytics
        String key = "social:share:plan:" + planId;
        stringRedisTemplate.opsForHash().increment(key, channel, 1);
        stringRedisTemplate.expire(key, 30, TimeUnit.DAYS);

        // Increment plan popularity in Redis for trending
        stringRedisTemplate.opsForZSet().incrementScore("social:trending:plans", planId.toString(), 1);
    }

    // ================================================================
    //  Companion matching
    // ================================================================

    /**
     * Publish a companion request with automatic matching.
     * Returns the created request plus a list of potential matches.
     */
    public Map<String, Object> publishCompanion(Long userId, Map<String, Object> params) {
        CompanionRequest req = new CompanionRequest();
        req.setUserId(userId);
        req.setDestination((String) params.get("destination"));
        req.setDateRange((String) params.get("dateRange"));
        req.setBudget(params.get("budget") != null ? Double.valueOf(params.get("budget").toString()) : null);
        req.setWeatherExpectation((String) params.get("weatherExpectation"));
        req.setCompanionProfile((String) params.get("companionProfile"));
        req.setStatus("active");
        companionRequestMapper.insert(req);

        // Find potential matches: same destination + overlapping dates
        List<CompanionRequest> matches = findCompanionMatches(req);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request", req);
        result.put("matches", matches);
        result.put("matchCount", matches.size());
        return result;
    }

    /**
     * Find existing open companion requests with same destination and overlapping date ranges.
     */
    private List<CompanionRequest> findCompanionMatches(CompanionRequest newReq) {
        if (newReq.getDestination() == null) return Collections.emptyList();

        LambdaQueryWrapper<CompanionRequest> qw = new LambdaQueryWrapper<>();
        qw.eq(CompanionRequest::getDestination, newReq.getDestination())
          .eq(CompanionRequest::getStatus, "active")
          .ne(CompanionRequest::getUserId, newReq.getUserId())  // exclude self
          .orderByDesc(CompanionRequest::getCreateTime);

        List<CompanionRequest> candidates = companionRequestMapper.selectList(qw);

        // Filter by date overlap
        return candidates.stream()
                .filter(c -> datesOverlap(newReq.getDateRange(), c.getDateRange()))
                .collect(Collectors.toList());
    }

    /**
     * Simple date overlap check. Format: "2026-03-01 ~ 2026-03-07" or similar.
     */
    private boolean datesOverlap(String range1, String range2) {
        if (range1 == null || range2 == null) return true; // be lenient
        try {
            String[] parts1 = range1.split("~");
            String[] parts2 = range2.split("~");
            if (parts1.length < 2 || parts2.length < 2) return false;

            LocalDate start1 = parseDate(parts1[0].trim());
            LocalDate end1 = parseDate(parts1[1].trim());
            LocalDate start2 = parseDate(parts2[0].trim());
            LocalDate end2 = parseDate(parts2[1].trim());

            return !end1.isBefore(start2) && !end2.isBefore(start1);
        } catch (Exception e) {
            return true; // be lenient on parse errors
        }
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            // Try common Chinese date format
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            } catch (Exception ex) {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-M-d"));
            }
        }
    }

    public List<CompanionRequest> getCompanionList(Integer page, Integer size) {
        LambdaQueryWrapper<CompanionRequest> qw = new LambdaQueryWrapper<>();
        qw.eq(CompanionRequest::getStatus, "active").orderByDesc(CompanionRequest::getCreateTime);
        return companionRequestMapper.selectPage(new Page<>(page, size), qw).getRecords();
    }

    // ================================================================
    //  Inspiration & Discovery
    // ================================================================

    /**
     * Get the user's inspiration value (accumulated from sharing, companion matching, etc.)
     */
    public Map<String, Object> getInspiration(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Calculate inspiration from Redis + DB
        String inspKey = "social:inspiration:" + userId;
        String cachedVal = stringRedisTemplate.opsForValue().get(inspKey);

        int total;
        if (cachedVal != null) {
            total = Integer.parseInt(cachedVal);
        } else {
            // Fallback: count share cards + companion requests
            Long cardCount = shareCardMapper.selectCount(
                    new LambdaQueryWrapper<ShareCard>().eq(ShareCard::getUserId, userId));
            Long reqCount = companionRequestMapper.selectCount(
                    new LambdaQueryWrapper<CompanionRequest>().eq(CompanionRequest::getUserId, userId));
            total = cardCount.intValue() * 5 + reqCount.intValue() * 3;
            stringRedisTemplate.opsForValue().set(inspKey, String.valueOf(total), 1, TimeUnit.HOURS);
        }

        // Get recent activity
        List<Map<String, Object>> history = new ArrayList<>();
        List<ShareCard> recentCards = shareCardMapper.selectList(
                new LambdaQueryWrapper<ShareCard>()
                        .eq(ShareCard::getUserId, userId)
                        .orderByDesc(ShareCard::getCreateTime)
                        .last("LIMIT 5"));
        for (ShareCard card : recentCards) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "share");
            entry.put("id", card.getId());
            entry.put("planId", card.getPlanId());
            entry.put("time", card.getCreateTime() != null ? card.getCreateTime().toString() : "");
            history.add(entry);
        }

        result.put("total", total);
        result.put("level", resolveInspirationLevel(total));
        result.put("history", history);
        return result;
    }

    private String resolveInspirationLevel(int total) {
        if (total >= 100) return "gold";
        if (total >= 50) return "silver";
        if (total >= 20) return "bronze";
        return "newbie";
    }

    /**
     * Top shared plans for discovery (popular routes).
     */
    public List<Map<String, Object>> getTopSharedPlans(int limit) {
        List<ShareCard> topCards = shareCardMapper.selectList(
                new LambdaQueryWrapper<ShareCard>()
                        .orderByDesc(ShareCard::getShareCount)
                        .last("LIMIT " + limit));

        return topCards.stream().map(card -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cardId", card.getId());
            item.put("planId", card.getPlanId());
            item.put("shareCount", card.getShareCount() != null ? card.getShareCount() : 0);
            item.put("imageUrl", card.getCardImageUrl());
            item.put("template", card.getTemplate());
            // Parse cardContent for destination/title
            try {
                JSONObject content = JSONUtil.parseObj(card.getCardContent());
                item.put("destination", content.getStr("destination", ""));
                item.put("title", content.getStr("title", ""));
            } catch (Exception ignored) {
                item.put("destination", "");
                item.put("title", "");
            }
            return item;
        }).collect(Collectors.toList());
    }

    // ================================================================
    //  Private helpers
    // ================================================================

    /**
     * Resolve plan data -- in production this calls the plan service.
     * For now it reads what's available from the plan via Redis cache or constructs defaults.
     */
    private PlanData resolvePlanData(Long planId, Long userId) {
        // Try Redis cache first
        String cacheKey = "social:plan:cache:" + planId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            try {
                JSONObject json = JSONUtil.parseObj(cached);
                return new PlanData(
                        json.getStr("destination", "未知目的地"),
                        json.getStr("title", "我的旅行"),
                        json.getStr("content", ""),
                        json.getStr("coverImage", null),
                        json.getDouble("totalBudget"),
                        json.getInt("totalDays")
                );
            } catch (Exception e) {
                log.warn("Failed to parse cached plan data for planId={}", planId, e);
            }
        }

        // Fallback: return placeholder data (real data comes from plan service via Feign in production)
        PlanData fallback = new PlanData("未知目的地", "精彩旅行", "", null, null, 0);

        // Cache the fallback briefly
        JSONObject cacheJson = new JSONObject();
        cacheJson.set("destination", fallback.destination);
        cacheJson.set("title", fallback.title);
        cacheJson.set("content", fallback.content);
        stringRedisTemplate.opsForValue().set(cacheKey, cacheJson.toString(), 5, TimeUnit.MINUTES);

        return fallback;
    }

    /**
     * Default cover image for common destinations.
     */
    private String getDefaultCoverForDestination(String destination) {
        if (!StringUtils.hasText(destination)) {
            return "https://img.travel-chart.app/defaults/travel-cover.jpg";
        }

        Map<String, String> defaultCovers = new LinkedHashMap<>();
        defaultCovers.put("北京", "https://img.travel-chart.app/destinations/beijing.jpg");
        defaultCovers.put("上海", "https://img.travel-chart.app/destinations/shanghai.jpg");
        defaultCovers.put("杭州", "https://img.travel-chart.app/destinations/hangzhou.jpg");
        defaultCovers.put("成都", "https://img.travel-chart.app/destinations/chengdu.jpg");
        defaultCovers.put("三亚", "https://img.travel-chart.app/destinations/sanya.jpg");
        defaultCovers.put("西安", "https://img.travel-chart.app/destinations/xian.jpg");
        defaultCovers.put("大理", "https://img.travel-chart.app/destinations/dali.jpg");
        defaultCovers.put("丽江", "https://img.travel-chart.app/destinations/lijiang.jpg");
        defaultCovers.put("厦门", "https://img.travel-chart.app/destinations/xiamen.jpg");
        defaultCovers.put("重庆", "https://img.travel-chart.app/destinations/chongqing.jpg");
        defaultCovers.put("桂林", "https://img.travel-chart.app/destinations/guilin.jpg");
        defaultCovers.put("张家界", "https://img.travel-chart.app/destinations/zhangjiajie.jpg");

        for (Map.Entry<String, String> entry : defaultCovers.entrySet()) {
            if (destination.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "https://img.travel-chart.app/defaults/travel-cover.jpg";
    }

    /**
     * Extract trip highlights from plan content JSON.
     */
    private List<String> extractHighlights(String contentJson) {
        List<String> highlights = new ArrayList<>();
        if (!StringUtils.hasText(contentJson)) {
            highlights.add("精彩旅程即将开启");
            highlights.add("探索未知的美好");
            return highlights;
        }

        try {
            JSONObject root = JSONUtil.parseObj(contentJson);
            // Try to extract from daily plans array
            JSONArray dailyPlans = root.getJSONArray("dailyPlans");
            if (dailyPlans != null && !dailyPlans.isEmpty()) {
                for (int i = 0; i < Math.min(dailyPlans.size(), 5); i++) {
                    JSONObject day = dailyPlans.getJSONObject(i);
                    String summary = day.getStr("summary", day.getStr("title", ""));
                    if (StringUtils.hasText(summary)) {
                        highlights.add(summary);
                    }
                }
                if (highlights.isEmpty()) {
                    highlights.add("Day 1: 抵达" + root.getStr("destination", "目的地"));
                }
            } else {
                // try poi list
                JSONArray pois = root.getJSONArray("pois");
                if (pois != null && !pois.isEmpty()) {
                    for (int i = 0; i < Math.min(pois.size(), 5); i++) {
                        JSONObject poi = pois.getJSONObject(i);
                        String name = poi.getStr("name", "");
                        if (StringUtils.hasText(name)) {
                            highlights.add(name);
                        }
                    }
                }
            }

            // Extract destination-level summary if available
            String overview = root.getStr("overview", root.getStr("summary", ""));
            if (StringUtils.hasText(overview) && highlights.size() < 3) {
                highlights.add(0, overview);
            }
        } catch (Exception e) {
            log.debug("Failed to parse plan content JSON for highlights: {}", e.getMessage());
        }

        if (highlights.isEmpty()) {
            highlights.add("精彩旅程即将开启");
            highlights.add("探索未知的美好");
        }
        return highlights;
    }

    /**
     * Build budget summary from plan data.
     */
    private Map<String, Object> buildBudgetSummary(PlanData planData) {
        Map<String, Object> budget = new LinkedHashMap<>();
        Double totalBudget = planData.totalBudget;

        if (totalBudget != null && totalBudget > 0) {
            budget.put("total", totalBudget);
            int days = Math.max(planData.totalDays, 1);
            budget.put("perDay", Math.round(totalBudget / days * 100.0) / 100.0);
            budget.put("days", days);

            // Budget level indicator
            double perDay = totalBudget / days;
            if (perDay <= 200) budget.put("level", "budget");
            else if (perDay <= 600) budget.put("level", "comfort");
            else if (perDay <= 1500) budget.put("level", "premium");
            else budget.put("level", "luxury");
        } else {
            budget.put("total", 0);
            budget.put("perDay", 0);
            budget.put("level", "unknown");
        }
        return budget;
    }

    /**
     * Fetch weather summary from weather microservice via Feign.
     * Falls back gracefully if the service is unavailable.
     */
    private Map<String, Object> fetchWeatherSummary(Long planId, String destination) {
        Map<String, Object> weather = new LinkedHashMap<>();
        try {
            var result = weatherFeign.getForecast(planId, destination);
            if (result != null && result.getCode() == 200 && result.getData() != null) {
                var forecasts = result.getData();
                if (!forecasts.isEmpty()) {
                    var first = forecasts.get(0);
                    weather.put("city", destination);
                    weather.put("tempHigh", first.getTempHigh());
                    weather.put("tempLow", first.getTempLow());
                    weather.put("conditionText", first.getConditionText());
                    weather.put("available", true);
                    return weather;
                }
            }
        } catch (Exception e) {
            log.warn("WeatherFeign call failed for planId={}, city={}: {}", planId, destination, e.getMessage());
        }

        // Graceful fallback
        weather.put("city", destination);
        weather.put("summary", "天气数据暂不可用");
        weather.put("available", false);
        return weather;
    }

    /**
     * Template-specific metadata for rendering hints.
     */
    private Map<String, Object> buildTemplateMeta(String template) {
        Map<String, Object> meta = new LinkedHashMap<>();
        switch (template) {
            case "long-image":
                meta.put("width", 750);
                meta.put("height", 1334);
                meta.put("aspectRatio", "9:16");
                meta.put("description", "适合朋友圈分享的长图");
                break;
            case "short-video":
                meta.put("duration", 15);
                meta.put("width", 1080);
                meta.put("height", 1920);
                meta.put("format", "mp4");
                meta.put("description", "15秒旅行短视频");
                break;
            case "instagram-story":
                meta.put("width", 1080);
                meta.put("height", 1920);
                meta.put("aspectRatio", "9:16");
                meta.put("description", "Instagram Story模板");
                break;
            case "xiaohongshu-note":
                meta.put("width", 1080);
                meta.put("height", 1440);
                meta.put("aspectRatio", "3:4");
                meta.put("description", "小红书笔记风格");
                break;
            default:
                meta.put("width", 750);
                meta.put("height", 1334);
                meta.put("aspectRatio", "9:16");
                meta.put("description", "通用分享模板");
        }
        return meta;
    }

    // ================================================================
    //  Internal data holder
    // ================================================================

    private static class PlanData {
        final String destination;
        final String title;
        final String content;
        final String coverImage;
        final Double totalBudget;
        final int totalDays;

        PlanData(String destination, String title, String content, String coverImage,
                 Double totalBudget, int totalDays) {
            this.destination = destination;
            this.title = title;
            this.content = content;
            this.coverImage = coverImage;
            this.totalBudget = totalBudget;
            this.totalDays = totalDays;
        }
    }
}
