package com.travelchart.userservice.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelchart.common.dto.GeneratePlanRequest;
import com.travelchart.userservice.entity.UserProfile;
import com.travelchart.userservice.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get existing profile or create a default one for the given user.
     */
    @Transactional
    public UserProfile getOrCreateProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile != null) {
            return profile;
        }

        UserProfile defaultProfile = new UserProfile();
        defaultProfile.setUserId(userId);
        defaultProfile.setPreferredThemes(toJson(defaultThemeWeights()));
        defaultProfile.setPreferredActivities(toJson(defaultActivityWeights()));
        defaultProfile.setBudgetPreference("comfort");
        defaultProfile.setPacePreference("compact");
        defaultProfile.setFavoriteCities(toJson(Collections.emptyList()));
        defaultProfile.setPreferredSeason(null);
        defaultProfile.setVisitCount(0);
        defaultProfile.setLastPlanTime(null);
        userProfileMapper.insert(defaultProfile);
        log.info("Created default profile for userId={}", userId);
        return defaultProfile;
    }

    /**
     * Update preference weights after each plan generation.
     * Increments theme usage counts, nudges budget/pace tendency toward the selected values.
     */
    @Transactional
    public void updateProfileOnPlanGeneration(Long userId, GeneratePlanRequest request) {
        UserProfile profile = getOrCreateProfile(userId);

        // Update theme preference weights
        Map<String, Double> themeWeights = fromJson(profile.getPreferredThemes(),
                new TypeReference<Map<String, Double>>() {});
        if (themeWeights == null) {
            themeWeights = defaultThemeWeights();
        }
        if (request.getThemes() != null) {
            for (String theme : request.getThemes()) {
                themeWeights.merge(theme, 1.0, Double::sum);
            }
        }
        profile.setPreferredThemes(toJson(themeWeights));

        // Update activity preference weights
        Map<String, Double> activityWeights = fromJson(profile.getPreferredActivities(),
                new TypeReference<Map<String, Double>>() {});
        if (activityWeights == null) {
            activityWeights = defaultActivityWeights();
        }
        if (request.getActivities() != null) {
            incrementActivityWeights(activityWeights, request);
        }
        profile.setPreferredActivities(toJson(activityWeights));

        // Update budget tendency: exponential moving average toward the selected level
        if (request.getBudget() != null && request.getBudget().getLevel() != null) {
            String newLevel = request.getBudget().getLevel();
            if (profile.getBudgetPreference() == null) {
                profile.setBudgetPreference(newLevel);
            } else if (!profile.getBudgetPreference().equals(newLevel)) {
                profile.setBudgetPreference(newLevel);
            }
        }

        // Update pace tendency
        if (request.getPace() != null) {
            profile.setPacePreference(request.getPace());
        }

        // Update favorite cities
        List<String> cityWeights = fromJson(profile.getFavoriteCities(),
                new TypeReference<List<String>>() {});
        if (cityWeights == null) {
            cityWeights = new ArrayList<>();
        }
        if (request.getDestinations() != null) {
            for (String dest : request.getDestinations()) {
                if (!cityWeights.contains(dest)) {
                    cityWeights.add(dest);
                }
            }
            // Keep only the most recent 10 cities
            if (cityWeights.size() > 10) {
                cityWeights = cityWeights.subList(cityWeights.size() - 10, cityWeights.size());
            }
        }
        profile.setFavoriteCities(toJson(cityWeights));

        // Update visit count and last plan time
        profile.setVisitCount(profile.getVisitCount() != null ? profile.getVisitCount() + 1 : 1);
        profile.setLastPlanTime(LocalDateTime.now());

        userProfileMapper.updateById(profile);
        log.info("Updated profile for userId={} after plan generation", userId);
    }

    /**
     * Derive smart default preferences from historical behavior data.
     * Returns a map of recommended preferences suitable for seeding a new plan request.
     */
    public Map<String, Object> getRecommendedPreferences(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        Map<String, Object> result = new LinkedHashMap<>();

        if (profile == null || profile.getVisitCount() == null || profile.getVisitCount() == 0) {
            // No history — return sensible defaults
            result.put("pace", "compact");
            result.put("budget", "comfort");
            result.put("topThemes", Arrays.asList("文化", "美食"));
            result.put("topActivities", defaultActivityWeights());
            result.put("isFromHistory", false);
            return result;
        }

        result.put("pace", profile.getPacePreference() != null ? profile.getPacePreference() : "compact");
        result.put("budget", profile.getBudgetPreference() != null ? profile.getBudgetPreference() : "comfort");

        // Top 3 themes by weight
        Map<String, Double> themeWeights = fromJson(profile.getPreferredThemes(),
                new TypeReference<Map<String, Double>>() {});
        if (themeWeights != null) {
            List<String> topThemes = themeWeights.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toList());
            result.put("topThemes", topThemes);
        }

        // Top activities by weight
        Map<String, Double> activityWeights = fromJson(profile.getPreferredActivities(),
                new TypeReference<Map<String, Double>>() {});
        if (activityWeights != null) {
            result.put("topActivities", activityWeights);
        }

        // Favorite cities
        List<String> cities = fromJson(profile.getFavoriteCities(),
                new TypeReference<List<String>>() {});
        if (cities != null && !cities.isEmpty()) {
            result.put("favoriteCities", cities);
        }

        // Preferred season
        if (profile.getPreferredSeason() != null) {
            result.put("preferredSeason", profile.getPreferredSeason());
        }

        result.put("visitCount", profile.getVisitCount());
        result.put("isFromHistory", true);
        return result;
    }

    /**
     * Directly persist profile updates (used by ProfileController when users manually edit preferences).
     */
    @Transactional
    public void updateProfileDirectly(UserProfile profile) {
        LambdaUpdateWrapper<UserProfile> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserProfile::getUserId, profile.getUserId());
        userProfileMapper.update(profile, wrapper);
        log.info("Directly updated profile for userId={}", profile.getUserId());
    }

    // ---- helper methods ----

    private Map<String, Double> defaultThemeWeights() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("文化", 1.0);
        map.put("美食", 1.0);
        map.put("自然", 1.0);
        map.put("城市", 1.0);
        map.put("亲子", 0.0);
        map.put("冒险", 0.0);
        map.put("休闲", 1.0);
        return map;
    }

    private Map<String, Double> defaultActivityWeights() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("eat", 1.0);
        map.put("drink", 0.5);
        map.put("play", 1.0);
        map.put("fun", 1.0);
        return map;
    }

    private void incrementActivityWeights(Map<String, Double> weights, GeneratePlanRequest request) {
        if (request.getActivities().getEat() != null && !request.getActivities().getEat().isEmpty()) {
            weights.merge("eat", 1.0, Double::sum);
        }
        if (request.getActivities().getDrink() != null && !request.getActivities().getDrink().isEmpty()) {
            weights.merge("drink", 1.0, Double::sum);
        }
        if (request.getActivities().getPlay() != null && !request.getActivities().getPlay().isEmpty()) {
            weights.merge("play", 1.0, Double::sum);
        }
        if (request.getActivities().getFun() != null && !request.getActivities().getFun().isEmpty()) {
            weights.merge("fun", 1.0, Double::sum);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON", e);
            return "{}";
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON: {}", json, e);
            return null;
        }
    }
}
