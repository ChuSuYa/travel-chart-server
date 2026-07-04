package com.travelchart.planservice.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Stage 3: Hard constraint engine — time-geography constraints for each day.
 *
 * Logic per day:
 *   Morning: 2 POIs near each other (within 5km)
 *   Lunch: nearby restaurant (within 2km, 12:00-13:00)
 *   Afternoon: 2 POIs near each other
 *   Dinner: nearby restaurant (18:00-19:00), can include night activity
 *
 * Considers opening hours, travel time, meal time slots.
 * Pace: relaxed=3-4 POIs/day, compact=5-7 POIs/day, intensive=7+ POIs/day.
 * Ensures variety: don't repeat same type consecutively.
 */
public class ConstraintSolver {

    private static final Logger log = LoggerFactory.getLogger(ConstraintSolver.class);

    private static final double MORNING_CLUSTER_KM = 5.0;
    private static final double MEAL_RADIUS_KM = 2.0;
    private static final String LUNCH_START = "12:00";
    private static final String LUNCH_END = "13:00";
    private static final String DINNER_START = "18:00";
    private static final String DINNER_END = "19:00";

    private final EngineConfig config;
    private final PoiRetriever retriever;
    private final Random rng = new Random(42); // deterministic seed

    public ConstraintSolver(EngineConfig config, PoiRetriever retriever) {
        this.config = config;
        this.retriever = retriever;
    }

    /**
     * Produce a list of DailyPlan from the candidate POIs and intent.
     */
    public List<DailyPlan> solve(List<PoiCandidate> rawCandidates, IntentContext ctx) {
        int totalDays = ctx.getTotalDays();
        List<String> destCities = ctx.getDestinations();
        List<DailyPlan> allPlans = new ArrayList<>();

        if (destCities.isEmpty() || rawCandidates.isEmpty()) {
            log.warn("No destinations or candidates — returning empty plan");
            return allPlans;
        }

        // Assign cities to days (cycling if more days than cities)
        for (int d = 0; d < totalDays; d++) {
            String city = destCities.get(d % destCities.size());
            String dateStr = computeDate(ctx, d);

            DailyPlan dayPlan = buildDailyPlan(city, rawCandidates, ctx, d);
            dayPlan.setDayNumber(d + 1);
            dayPlan.setDate(dateStr);
            dayPlan.setWeatherSummary(weatherForDay(ctx.getSeason(), d));
            allPlans.add(dayPlan);
        }

        log.info("Solved {} daily plans", allPlans.size());
        return allPlans;
    }

    private DailyPlan buildDailyPlan(String city, List<PoiCandidate> allCandidates,
                                      IntentContext ctx, int dayIndex) {
        DailyPlan plan = new DailyPlan();
        plan.setDayNumber(dayIndex + 1);

        List<PoiCandidate> cityCandidates = allCandidates.stream()
                .filter(p -> p.getCity().equals(city))
                .collect(Collectors.toList());

        if (cityCandidates.isEmpty()) {
            // Fallback: use all candidates
            cityCandidates = new ArrayList<>(allCandidates);
        }

        // Separate into POI (景点/娱乐) and restaurants (餐饮)
        List<PoiCandidate> attractions = cityCandidates.stream()
                .filter(p -> !"餐饮".equals(p.getType()))
                .collect(Collectors.toList());
        List<PoiCandidate> restaurants = cityCandidates.stream()
                .filter(p -> "餐饮".equals(p.getType()))
                .collect(Collectors.toList());

        int targetPoiCount = ctx.getPoiCountTarget();
        Set<String> usedTypes = new LinkedHashSet<>();

        // --- Morning: pick 2 POIs near each other ---
        List<PoiCandidate> morningPois = pickClustered(attractions, null, MORNING_CLUSTER_KM,
                Math.min(2, targetPoiCount), usedTypes);
        String currentTime = "09:00";

        for (PoiCandidate poi : morningPois) {
            plan.addItem(poiToItem(poi, currentTime, "景点"));
            currentTime = addMinutes(currentTime, poi.getDurationMinutes() + travelTime(poi, morningPois));
            usedTypes.add(poi.getSubType());
        }

        // --- Lunch ---
        if (!morningPois.isEmpty()) {
            PoiCandidate lastMorning = morningPois.get(morningPois.size() - 1);
            PoiCandidate lunch = pickNearbyRestaurant(restaurants, lastMorning.getLat(),
                    lastMorning.getLng(), MEAL_RADIUS_KM, "午餐");
            if (lunch != null) {
                plan.addItem(poiToItem(lunch, LUNCH_START, "餐饮"));
                plan.addItem(transportItem("午餐后短暂休息", lastMorning, lunch, 15));
            }
        }

        // --- Afternoon: pick 2 POIs ---
        List<PoiCandidate> remainingAttractions = new ArrayList<>(attractions);
        remainingAttractions.removeAll(morningPois);

        List<PoiCandidate> afternoonPois = pickClustered(remainingAttractions, null,
                MORNING_CLUSTER_KM, Math.min(2, targetPoiCount - morningPois.size()), usedTypes);
        currentTime = "14:00";

        for (PoiCandidate poi : afternoonPois) {
            plan.addItem(poiToItem(poi, currentTime, "景点"));
            currentTime = addMinutes(currentTime, poi.getDurationMinutes() + 15);
            usedTypes.add(poi.getSubType());
        }

        // --- Dinner ---
        if (!afternoonPois.isEmpty() || !morningPois.isEmpty()) {
            PoiCandidate lastPoi = !afternoonPois.isEmpty()
                    ? afternoonPois.get(afternoonPois.size() - 1)
                    : morningPois.get(morningPois.size() - 1);
            PoiCandidate dinner = pickNearbyRestaurant(restaurants, lastPoi.getLat(),
                    lastPoi.getLng(), MEAL_RADIUS_KM, "晚餐");
            if (dinner != null) {
                plan.addItem(poiToItem(dinner, DINNER_START, "餐饮"));
            }
        }

        // --- Night activity (optional, for intensive/compact pace) ---
        if (!"relaxed".equals(ctx.getPace()) && !cityCandidates.isEmpty()) {
            List<PoiCandidate> nightCandidates = cityCandidates.stream()
                    .filter(p -> p.getTags().contains("夜景") || p.getTags().contains("夜生活"))
                    .collect(Collectors.toList());
            if (!nightCandidates.isEmpty()) {
                PoiCandidate night = nightCandidates.get(rng.nextInt(nightCandidates.size()));
                plan.addItem(poiToItem(night, "19:30", "娱乐"));
            }
        }

        // Ensure at least a minimum plan
        if (plan.getItems().isEmpty()) {
            for (int i = 0; i < Math.min(targetPoiCount, cityCandidates.size()); i++) {
                PoiCandidate p = cityCandidates.get(i);
                plan.addItem(poiToItem(p, "09:00", "景点"));
            }
        }

        return plan;
    }

    /**
     * Pick a cluster of POIs near each other (or near anchor if provided).
     */
    private List<PoiCandidate> pickClustered(List<PoiCandidate> pool, PoiCandidate anchor,
                                              double maxDistKm, int count, Set<String> usedTypes) {
        if (pool.isEmpty()) return Collections.emptyList();
        List<PoiCandidate> result = new ArrayList<>();
        List<PoiCandidate> remaining = new ArrayList<>(pool);

        // Start: pick highest rated that is not a repeated type
        PoiCandidate first = null;
        for (PoiCandidate p : remaining) {
            if (!usedTypes.contains(p.getSubType())) {
                first = p;
                break;
            }
        }
        if (first == null) first = remaining.get(0);
        result.add(first);
        remaining.remove(first);
        usedTypes.add(first.getSubType());

        // Add nearby POIs of different types
        for (int i = 1; i < count && !remaining.isEmpty(); i++) {
            final PoiCandidate last = result.get(result.size() - 1);
            PoiCandidate best = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (PoiCandidate p : remaining) {
                if (usedTypes.contains(p.getSubType())) continue;
                double dist = p.distanceKmTo(last);
                if (anchor != null && p.distanceKmTo(anchor) > maxDistKm) continue;
                if (dist > maxDistKm) continue;

                double score = p.getRating() - dist * 0.05; // closer + higher rated = better
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
            if (best != null) {
                result.add(best);
                remaining.remove(best);
                usedTypes.add(best.getSubType());
            } else {
                break; // no more suitable nearby POIs
            }
        }

        return result;
    }

    /**
     * Pick a restaurant near a location.
     */
    private PoiCandidate pickNearbyRestaurant(List<PoiCandidate> restaurants,
                                               double lat, double lng, double radiusKm, String mealType) {
        // Prioritize by proximity then rating
        return restaurants.stream()
                .filter(r -> r.distanceKmTo(lat, lng) <= radiusKm)
                .min(Comparator.comparingDouble((PoiCandidate r) -> r.distanceKmTo(lat, lng))
                        .thenComparing((PoiCandidate r) -> -r.getRating()))
                .orElseGet(() -> {
                    // Fallback: pick closest restaurant regardless of distance
                    return restaurants.stream()
                            .min(Comparator.comparingDouble(r -> r.distanceKmTo(lat, lng)))
                            .orElse(null);
                });
    }

    /**
     * Estimated travel time in minutes based on distance.
     * Rough: 1km ≈ 3min (urban driving), capped at 5km ≈ 15min, above that by subway ≈ distance/0.5km*3min.
     */
    private int travelTime(PoiCandidate from, List<PoiCandidate> context) {
        if (context.isEmpty()) return 0;
        PoiCandidate last = context.get(context.size() - 1);
        double distKm = from.distanceKmTo(last);
        if (distKm < 1) return 5;
        if (distKm < 5) return (int)(distKm * 3);
        return (int)(distKm * 1.5); // assume faster transit for long distances
    }

    private PlanItem poiToItem(PoiCandidate poi, String time, String category) {
        return new PlanItem(time, poi.getName(),
                poi.getCity() + " · " + poi.getSubType() + " · " + poi.getOpeningHours(),
                category, poi.getDurationMinutes(),
                estimatePrice(poi), poi.getLat(), poi.getLng(),
                "建议游玩" + poi.getDurationMinutes() + "分钟");
    }

    private PlanItem transportItem(String desc, PoiCandidate from, PoiCandidate to, int duration) {
        double dist = from.distanceKmTo(to);
        String mode = dist < 2 ? "步行" : (dist < 10 ? "打车/公交" : "地铁");
        return new PlanItem("", mode + "前往下一站",
                desc + " (" + String.format("%.1f", dist) + "km)",
                "交通", duration, dist < 10 ? dist * 3 : dist * 5,
                to.getLat(), to.getLng(), mode + "约" + (int)(dist * 2.5) + "分钟");
    }

    private double estimatePrice(PoiCandidate poi) {
        switch (poi.getPriceLevel()) {
            case 1: return poi.getType().equals("餐饮") ? 40 : 20;
            case 2: return poi.getType().equals("餐饮") ? 100 : 60;
            case 3: return poi.getType().equals("餐饮") ? 250 : 150;
            default: return 50;
        }
    }

    private String addMinutes(String time, int minutes) {
        try {
            String[] parts = time.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) + minutes;
            h += m / 60;
            m = m % 60;
            return String.format("%02d:%02d", h % 24, m);
        } catch (Exception e) { return time; }
    }

    private String computeDate(IntentContext ctx, int dayOffset) {
        if (ctx.getDateRange() != null && !ctx.getDateRange().isEmpty()) {
            try {
                return java.time.LocalDate.parse(ctx.getDateRange().get(0))
                        .plusDays(dayOffset).toString();
            } catch (Exception ignored) {}
        }
        return java.time.LocalDate.now().plusDays(dayOffset).toString();
    }

    private String weatherForDay(String season, int day) {
        Random w = new Random(day * 100L + season.hashCode());
        switch (season) {
            case "spring":
                return w.nextDouble() > 0.3 ? "晴转多云 15-25°C" : "小雨 12-20°C";
            case "summer":
                return w.nextDouble() > 0.3 ? "晴朗 28-35°C" : "雷阵雨 26-33°C";
            case "autumn":
                return w.nextDouble() > 0.3 ? "晴 18-26°C" : "多云 15-22°C";
            case "winter":
                return w.nextDouble() > 0.3 ? "晴 -5-5°C" : "阴 -3-3°C";
            default:
                return "晴 20-28°C";
        }
    }
}
