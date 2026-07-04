package com.travelchart.socialservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelchart.socialservice.entity.CompanionRequest;
import com.travelchart.socialservice.entity.ShareCard;
import com.travelchart.socialservice.mapper.CompanionRequestMapper;
import com.travelchart.socialservice.mapper.ShareCardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Discovery feed service.
 *
 * Returns a curated mix of content for the user's discover page:
 * banners, categories, trending plans, inspiration cards, and companion highlights.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoverService {

    private final ShareCardMapper shareCardMapper;
    private final CompanionRequestMapper companionRequestMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_TRENDING_KEY = "social:trending:destinations";
    private static final String REDIS_POPULAR_PLANS_KEY = "social:trending:plans";

    /**
     * Build a curated discover feed for the given user.
     *
     * Returns: banners (2), categories (6), trending plans (10),
     * inspiration cards (3), companion highlights (3).
     */
    public Map<String, Object> getDiscoverFeed(Long userId) {
        Map<String, Object> feed = new LinkedHashMap<>();

        feed.put("banners", getBanners());
        feed.put("categories", getCategories());
        feed.put("trendingPlans", getTrendingPlans());
        feed.put("inspirationCards", getInspirationalContent());
        feed.put("companionHighlights", getCompanionHighlights());
        feed.put("timestamp", System.currentTimeMillis());

        return feed;
    }

    /**
     * Top trending destinations sorted by recent plan count via Redis sorted set.
     */
    public List<Map<String, Object>> getTrendingDestinations() {
        // Get top destinations from Redis sorted set
        Set<String> topCities = stringRedisTemplate.opsForZSet()
                .reverseRange(REDIS_TRENDING_KEY, 0, 14);

        List<Map<String, Object>> result = new ArrayList<>();

        if (topCities != null && !topCities.isEmpty()) {
            for (String city : topCities) {
                Double score = stringRedisTemplate.opsForZSet().score(REDIS_TRENDING_KEY, city);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("city", city);
                item.put("planCount", score != null ? score.longValue() : 0);
                item.put("imageUrl", getCityImage(city));
                result.add(item);
            }
        } else {
            // Seed with defaults and populate Redis
            seedTrendingDestinations();
            return getTrendingDestinations();
        }

        return result;
    }

    /**
     * Inspirational content: travel quotes, photography tips, cultural facts.
     */
    public List<Map<String, Object>> getInspirationalContent() {
        List<Map<String, Object>> cards = new ArrayList<>();

        cards.add(buildInspirationCard(
                "旅行摄影小贴士",
                "想要拍出大片感？记住黄金时刻——日出后一小时和日落前一小时，光线最柔和。",
                "photo",
                "https://img.travel-chart.app/inspiration/photo-tips.jpg"
        ));

        cards.add(buildInspirationCard(
                "旅行金句",
                "\"世界是一本书，不旅行的人只读了其中一页。\" —— 圣奥古斯丁",
                "quote",
                "https://img.travel-chart.app/inspiration/quote-01.jpg"
        ));

        cards.add(buildInspirationCard(
                "文化小知识",
                "你知道吗？故宫共有9999.5间房，比传说中天宫的一万间少半间，以表对天的敬意。",
                "culture",
                "https://img.travel-chart.app/inspiration/culture-forbidden-city.jpg"
        ));

        cards.add(buildInspirationCard(
                "旅行金句",
                "\"旅行的意义不在于目的地，而在于沿途的风景和看风景的心情。\"",
                "quote",
                "https://img.travel-chart.app/inspiration/quote-02.jpg"
        ));

        cards.add(buildInspirationCard(
                "旅行小知识",
                "日本JR PASS可以在7天内无限次乘坐新干线，是游览日本最划算的方式之一。",
                "culture",
                "https://img.travel-chart.app/inspiration/culture-japan.jpg"
        ));

        // Randomize and return 3
        Collections.shuffle(cards);
        return cards.subList(0, Math.min(3, cards.size()));
    }

    // ================================================================
    //  Private feed sections
    // ================================================================

    private List<Map<String, Object>> getBanners() {
        List<Map<String, Object>> banners = new ArrayList<>();

        Map<String, Object> banner1 = new LinkedHashMap<>();
        banner1.put("id", 1);
        banner1.put("title", "这个夏天，去海边吧");
        banner1.put("subtitle", "三亚、厦门、青岛热门攻略");
        banner1.put("imageUrl", "https://img.travel-chart.app/banners/summer-beach.jpg");
        banner1.put("action", "discover");
        banner1.put("actionParams", Map.of("tag", "beach"));

        Map<String, Object> banner2 = new LinkedHashMap<>();
        banner2.put("id", 2);
        banner2.put("title", "古都文化之旅");
        banner2.put("subtitle", "穿越千年，探索中华文明");
        banner2.put("imageUrl", "https://img.travel-chart.app/banners/ancient-capitals.jpg");
        banner2.put("action", "discover");
        banner2.put("actionParams", Map.of("tag", "culture"));

        banners.add(banner1);
        banners.add(banner2);
        return banners;
    }

    private List<Map<String, Object>> getCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();

        categories.add(categoryItem("beach", "海岛度假", "🏖️", "https://img.travel-chart.app/categories/beach.jpg"));
        categories.add(categoryItem("mountain", "登山徒步", "🏔️", "https://img.travel-chart.app/categories/mountain.jpg"));
        categories.add(categoryItem("ancient", "古镇园林", "🏯", "https://img.travel-chart.app/categories/ancient.jpg"));
        categories.add(categoryItem("food", "美食之旅", "🍜", "https://img.travel-chart.app/categories/food.jpg"));
        categories.add(categoryItem("culture", "文化探索", "🏛️", "https://img.travel-chart.app/categories/culture.jpg"));
        categories.add(categoryItem("photo", "网红打卡", "📸", "https://img.travel-chart.app/categories/photo.jpg"));

        return categories;
    }

    private List<Map<String, Object>> getTrendingPlans() {
        // Get top 10 from Redis sorted set, fall back to DB
        Set<String> topPlanIds = stringRedisTemplate.opsForZSet()
                .reverseRange(REDIS_POPULAR_PLANS_KEY, 0, 9);

        List<Map<String, Object>> plans = new ArrayList<>();

        if (topPlanIds != null && !topPlanIds.isEmpty()) {
            for (String planIdStr : topPlanIds) {
                try {
                    Long planId = Long.parseLong(planIdStr);
                    ShareCard card = shareCardMapper.selectOne(
                            new LambdaQueryWrapper<ShareCard>()
                                    .eq(ShareCard::getPlanId, planId)
                                    .orderByDesc(ShareCard::getCreateTime)
                                    .last("LIMIT 1"));
                    if (card != null) {
                        Double score = stringRedisTemplate.opsForZSet().score(REDIS_POPULAR_PLANS_KEY, planIdStr);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("cardId", card.getId());
                        item.put("planId", card.getPlanId());
                        item.put("shareCount", score != null ? score.longValue() : 0);
                        item.put("imageUrl", card.getCardImageUrl());
                        item.put("template", card.getTemplate());
                        plans.add(item);
                    }
                } catch (Exception e) {
                    log.debug("Failed to load trending plan {}: {}", planIdStr, e.getMessage());
                }
            }
        }

        // Fallback: load from DB directly
        if (plans.isEmpty()) {
            List<ShareCard> topCards = shareCardMapper.selectList(
                    new LambdaQueryWrapper<ShareCard>()
                            .orderByDesc(ShareCard::getShareCount)
                            .last("LIMIT 10"));
            for (ShareCard card : topCards) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("cardId", card.getId());
                item.put("planId", card.getPlanId());
                item.put("shareCount", card.getShareCount() != null ? card.getShareCount() : 0);
                item.put("imageUrl", card.getCardImageUrl());
                item.put("template", card.getTemplate());
                plans.add(item);
            }
        }

        return plans;
    }

    /**
     * Highlighted companion requests for the discover feed.
     */
    private List<Map<String, Object>> getCompanionHighlights() {
        List<CompanionRequest> activeRequests = companionRequestMapper.selectList(
                new LambdaQueryWrapper<CompanionRequest>()
                        .eq(CompanionRequest::getStatus, "active")
                        .orderByDesc(CompanionRequest::getCreateTime)
                        .last("LIMIT 3"));

        return activeRequests.stream().map(req -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", req.getId());
            item.put("destination", req.getDestination());
            item.put("dateRange", req.getDateRange());
            item.put("budget", req.getBudget());
            item.put("profile", req.getCompanionProfile());
            return item;
        }).collect(Collectors.toList());
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private Map<String, Object> buildInspirationCard(String title, String content, String type, String imageUrl) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", title);
        card.put("content", content);
        card.put("type", type);
        card.put("imageUrl", imageUrl);
        return card;
    }

    private Map<String, Object> categoryItem(String tag, String name, String icon, String imageUrl) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tag", tag);
        item.put("name", name);
        item.put("icon", icon);
        item.put("imageUrl", imageUrl);
        return item;
    }

    private String getCityImage(String city) {
        Map<String, String> cityImages = new LinkedHashMap<>();
        cityImages.put("北京", "https://img.travel-chart.app/destinations/beijing.jpg");
        cityImages.put("上海", "https://img.travel-chart.app/destinations/shanghai.jpg");
        cityImages.put("杭州", "https://img.travel-chart.app/destinations/hangzhou.jpg");
        cityImages.put("成都", "https://img.travel-chart.app/destinations/chengdu.jpg");
        cityImages.put("三亚", "https://img.travel-chart.app/destinations/sanya.jpg");
        cityImages.put("西安", "https://img.travel-chart.app/destinations/xian.jpg");
        cityImages.put("大理", "https://img.travel-chart.app/destinations/dali.jpg");
        cityImages.put("丽江", "https://img.travel-chart.app/destinations/lijiang.jpg");
        cityImages.put("厦门", "https://img.travel-chart.app/destinations/xiamen.jpg");
        cityImages.put("重庆", "https://img.travel-chart.app/destinations/chongqing.jpg");
        cityImages.put("桂林", "https://img.travel-chart.app/destinations/guilin.jpg");
        cityImages.put("昆明", "https://img.travel-chart.app/destinations/kunming.jpg");
        cityImages.put("青岛", "https://img.travel-chart.app/destinations/qingdao.jpg");
        cityImages.put("张家界", "https://img.travel-chart.app/destinations/zhangjiajie.jpg");
        cityImages.put("拉萨", "https://img.travel-chart.app/destinations/lhasa.jpg");

        for (Map.Entry<String, String> entry : cityImages.entrySet()) {
            if (city.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "https://img.travel-chart.app/defaults/city-default.jpg";
    }

    /**
     * Seed trending destinations with initial data.
     */
    private void seedTrendingDestinations() {
        Map<String, Double> seeds = new LinkedHashMap<>();
        seeds.put("三亚", 120.0);
        seeds.put("北京", 105.0);
        seeds.put("成都", 98.0);
        seeds.put("杭州", 91.0);
        seeds.put("西安", 85.0);
        seeds.put("厦门", 78.0);
        seeds.put("大理", 72.0);
        seeds.put("丽江", 65.0);
        seeds.put("重庆", 58.0);
        seeds.put("桂林", 50.0);
        seeds.put("上海", 45.0);
        seeds.put("昆明", 42.0);
        seeds.put("青岛", 38.0);
        seeds.put("张家界", 35.0);
        seeds.put("拉萨", 30.0);

        for (Map.Entry<String, Double> entry : seeds.entrySet()) {
            stringRedisTemplate.opsForZSet().add(REDIS_TRENDING_KEY, entry.getKey(), entry.getValue());
        }
        stringRedisTemplate.expire(REDIS_TRENDING_KEY, 7, TimeUnit.DAYS);
    }
}
