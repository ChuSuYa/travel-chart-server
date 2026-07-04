package com.travelchart.planservice.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Stage 4: Soft constraint multi-objective optimization.
 *
 * Scores daily plans by:
 *   - Diversity of POI types
 *   - Geographic proximity (minimize travel distance)
 *   - Budget adherence
 *   - Theme match score
 *   - Weather suitability
 *
 * Applies weighted scoring to select best candidates.
 * Adds "surprise points" (1-2 per trip): less popular but highly-rated spots.
 */
public class RouteOptimizer {

    private static final Logger log = LoggerFactory.getLogger(RouteOptimizer.class);

    private final EngineConfig config;
    private final PoiRetriever retriever;
    private final Random rng = new Random(123);

    public RouteOptimizer(EngineConfig config, PoiRetriever retriever) {
        this.config = config;
        this.retriever = retriever;
    }

    /**
     * Optimize the daily plans: re-rank items within each day, add surprise POIs,
     * and compute composite scores.
     */
    public List<DailyPlan> optimize(List<DailyPlan> plans, IntentContext ctx, List<PoiCandidate> candidates) {
        for (DailyPlan day : plans) {
            optimizeDay(day, ctx, candidates);
        }

        // Add surprise POI(s) — 1-2 across the whole trip
        addSurprisePOIs(plans, ctx, candidates);

        log.info("Optimized {} daily plans", plans.size());
        return plans;
    }

    private void optimizeDay(DailyPlan day, IntentContext ctx, List<PoiCandidate> candidates) {
        List<PlanItem> items = day.getItems();
        if (items.isEmpty()) return;

        // Separate into POIs and meals (meals stay fixed in their slots)
        List<PlanItem> pois = items.stream()
                .filter(i -> "景点".equals(i.getCategory()) || "娱乐".equals(i.getCategory()))
                .collect(Collectors.toList());
        List<PlanItem> mealsAndTransit = items.stream()
                .filter(i -> !"景点".equals(i.getCategory()) && !"娱乐".equals(i.getCategory()))
                .toList();

        if (pois.size() <= 1) return;

        // Reorder POIs within the day for optimal diversity + proximity
        List<PlanItem> reordered = reorderForDiversity(pois);

        // Rebuild day items: interleave meals at their original positions
        List<PlanItem> newItems = new ArrayList<>();
        int poiIdx = 0;
        for (PlanItem item : items) {
            if ("景点".equals(item.getCategory()) || "娱乐".equals(item.getCategory())) {
                if (poiIdx < reordered.size()) {
                    // Update time slot
                    PlanItem r = reordered.get(poiIdx);
                    newItems.add(r);
                    poiIdx++;
                }
            } else {
                newItems.add(item);
            }
        }
        // Append any remaining reordered POIs
        while (poiIdx < reordered.size()) {
            newItems.add(reordered.get(poiIdx++));
        }

        day.setItems(newItems);
    }

    /**
     * Reorder POIs to maximize type diversity and geographic proximity.
     * Greedy nearest-neighbor with subType diversity penalty.
     */
    private List<PlanItem> reorderForDiversity(List<PlanItem> pois) {
        if (pois.isEmpty()) return pois;
        List<PlanItem> ordered = new ArrayList<>();
        List<PlanItem> remaining = new ArrayList<>(pois);
        Set<String> usedTypes = new LinkedHashSet<>();

        // Start with highest-rated
        PlanItem best = remaining.get(0);
        for (PlanItem p : remaining) {
            if (p.getCost() > best.getCost()) best = p; // proxy for rating via cost
        }
        ordered.add(best);
        remaining.remove(best);
        usedTypes.add(best.getName());

        while (!remaining.isEmpty()) {
            PlanItem last = ordered.get(ordered.size() - 1);
            PlanItem choice = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (PlanItem p : remaining) {
                double dist = distKm(last, p);
                double distScore = 1.0 / (1.0 + dist);

                // Penalize same type (estimated by name similarity)
                double diversityPenalty = usedTypes.contains(p.getName()) ? -1.0 : 0.0;

                double score = distScore * 0.6 + diversityPenalty * 0.4;
                if (score > bestScore) {
                    bestScore = score;
                    choice = p;
                }
            }

            if (choice != null) {
                ordered.add(choice);
                remaining.remove(choice);
                usedTypes.add(choice.getName());
            } else {
                break;
            }
        }
        ordered.addAll(remaining);
        return ordered;
    }

    /**
     * Compute overall score for a daily plan using EngineConfig weights.
     */
    public double scoreDay(DailyPlan day, IntentContext ctx, List<PoiCandidate> candidates) {
        if (day.getItems().isEmpty()) return 0;

        double diversity = computeDiversity(day);
        double proximity = computeProximity(day);
        double budget = computeBudgetScore(day, ctx);
        double theme = computeThemeScore(day, ctx, candidates);
        double weather = computeWeatherScore(day, ctx);

        return config.getDiversityWeight() * diversity
                + config.getProximityWeight() * proximity
                + config.getBudgetWeight() * budget
                + config.getThemeWeight() * theme
                + config.getWeatherWeight() * weather;
    }

    private double computeDiversity(DailyPlan day) {
        Set<String> types = new LinkedHashSet<>();
        for (PlanItem item : day.getItems()) {
            types.add(item.getCategory() + ":" + item.getName());
        }
        int unique = types.size();
        int total = day.getItems().size();
        return total > 0 ? Math.min(1.0, (double) unique / total) : 0;
    }

    private double computeProximity(DailyPlan day) {
        double totalDist = 0;
        List<PlanItem> pois = day.getItems();
        for (int i = 1; i < pois.size(); i++) {
            totalDist += distKm(pois.get(i - 1), pois.get(i));
        }
        // Score: lower distance = higher score (max at 0km → 1.0, 50km → ~0)
        return Math.max(0, 1.0 - totalDist / 50.0);
    }

    private double computeBudgetScore(DailyPlan day, IntentContext ctx) {
        double dayCost = day.totalCost();
        if (ctx.getBudgetAmount() == null || ctx.getBudgetAmount() <= 0) return 0.8;
        double perDayBudget = (double) ctx.getBudgetAmount() / Math.max(1, ctx.getTotalDays());
        // Score: at budget → 1.0, over budget → penalty
        if (dayCost <= perDayBudget) return 1.0;
        return Math.max(0, 1.0 - (dayCost - perDayBudget) / perDayBudget);
    }

    private double computeThemeScore(DailyPlan day, IntentContext ctx, List<PoiCandidate> candidates) {
        if (ctx.getThemes().isEmpty()) return 0.5;
        double matches = 0;
        for (PlanItem item : day.getItems()) {
            for (PoiCandidate c : candidates) {
                if (c.getName().equals(item.getName())) {
                    for (String theme : ctx.getThemes()) {
                        // theme-to-tag matching (same logic as PoiRetriever)
                        if (c.getTags().stream().anyMatch(t -> matchesTheme(t, theme))) {
                            matches++;
                        }
                    }
                    break;
                }
            }
        }
        int total = Math.max(1, day.getItems().size());
        return Math.min(1.0, matches / total);
    }

    private boolean matchesTheme(String tag, String theme) {
        Map<String, List<String>> mapping = Map.of(
                "history", List.of("世界遗产", "历史", "古迹", "寺庙", "宫殿", "三国文化", "佛教", "白族文化"),
                "food", List.of("小吃", "老字号", "麻辣", "火锅", "烤鸭", "海鲜", "茶馆", "清真", "地道"),
                "outdoor", List.of("徒步", "骑行", "公园", "海滩", "潜水", "缆车"),
                "shopping", List.of("购物", "手信"),
                "family", List.of("亲子", "乐园", "动物园"),
                "romantic", List.of("浪漫", "夜景", "日落", "湖景", "温泉"),
                "photography", List.of("摄影", "夜景"),
                "adventure", List.of("潜水", "漂流", "徒步", "攀岩", "水上运动")
        );
        List<String> keywords = mapping.getOrDefault(theme, Collections.emptyList());
        return keywords.stream().anyMatch(tag::contains);
    }

    private double computeWeatherScore(DailyPlan day, IntentContext ctx) {
        // Simple: if season has good weather for outdoor items, score higher
        if (day.getItems().stream().anyMatch(i -> "景点".equals(i.getCategory()))) {
            return "winter".equals(ctx.getSeason()) ? 0.5 : 0.9;
        }
        return 0.8;
    }

    /**
     * Add 1-2 "surprise" POIs — less popular but highly-rated hidden gems.
     */
    private void addSurprisePOIs(List<DailyPlan> plans, IntentContext ctx, List<PoiCandidate> candidates) {
        int surpriseCount = ctx.getTotalDays() <= 2 ? 1 : 2;

        // Find candidates not already in the plan, with high rating but less prominence
        Set<String> usedNames = new LinkedHashSet<>();
        for (DailyPlan d : plans) {
            for (PlanItem i : d.getItems()) {
                usedNames.add(i.getName());
            }
        }

        List<PoiCandidate> surprisePool = candidates.stream()
                .filter(p -> !usedNames.contains(p.getName()))
                .filter(p -> p.getRating() >= 4.3)
                .filter(p -> "景点".equals(p.getType()) || "娱乐".equals(p.getType()))
                .sorted(Comparator.comparingDouble(PoiCandidate::getRating).reversed())
                .collect(Collectors.toList());

        int added = 0;
        for (DailyPlan day : plans) {
            if (added >= surpriseCount) break;

            // Pick a surprise POI near this day's last POI
            List<PlanItem> items = day.getItems();
            PoiCandidate anchor = null;
            double anchorLat = 0, anchorLng = 0;

            // Find last non-meal item's coordinates
            for (int i = items.size() - 1; i >= 0; i--) {
                PlanItem item = items.get(i);
                if ("景点".equals(item.getCategory()) || "娱乐".equals(item.getCategory())) {
                    anchorLat = item.getLat();
                    anchorLng = item.getLng();
                    break;
                }
            }

            // Pick a surprise POI nearby
            PoiCandidate surprise = pickSurprise(surprisePool, anchorLat, anchorLng, 8.0);
            if (surprise != null) {
                PlanItem surpriseItem = new PlanItem("16:00", surprise.getName(),
                        "✨ 隐藏推荐: " + surprise.getSubType() + " | 评分 " + surprise.getRating(),
                        "景点", surprise.getDurationMinutes(),
                        0, surprise.getLat(), surprise.getLng(),
                        "探索达人私藏好去处，避开人群享受纯粹体验");
                day.getItems().add(surpriseItem);
                surprisePool.remove(surprise);
                usedNames.add(surprise.getName());
                added++;
            }
        }

        if (added > 0) {
            log.info("Added {} surprise POIs", added);
        }
    }

    private PoiCandidate pickSurprise(List<PoiCandidate> pool, double lat, double lng, double maxKm) {
        List<PoiCandidate> nearby = pool.stream()
                .sorted(Comparator.comparingDouble(p -> {
                    double d = lat > 0 ? p.distanceKmTo(lat, lng) : 0;
                    // Optimize for: moderate distance (not too close, not too far) + high rating
                    return -(p.getRating() * 2 - Math.min(d, maxKm) * 0.3);
                }))
                .collect(Collectors.toList());

        return nearby.isEmpty() ? null : nearby.get(0);
    }

    private double distKm(PlanItem a, PlanItem b) {
        double dLat = Math.toRadians(b.getLat() - a.getLat());
        double dLng = Math.toRadians(b.getLng() - a.getLng());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.getLat())) * Math.cos(Math.toRadians(b.getLat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
    }
}
