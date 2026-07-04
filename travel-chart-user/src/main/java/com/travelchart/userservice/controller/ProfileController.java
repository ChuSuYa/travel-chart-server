package com.travelchart.userservice.controller;

import com.travelchart.common.result.Result;
import com.travelchart.common.util.JwtUtil;
import com.travelchart.userservice.entity.UserProfile;
import com.travelchart.userservice.profile.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileService userProfileService;
    private final JwtUtil jwtUtil;

    /**
     * Get user preferences/profile.
     */
    @GetMapping("/preferences")
    public Result<Map<String, Object>> getPreferences(@RequestHeader("Authorization") String auth) {
        Long userId = extractUserId(auth);
        UserProfile profile = userProfileService.getOrCreateProfile(userId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("userId", profile.getUserId());
        result.put("preferredThemes", profile.getPreferredThemes());
        result.put("preferredActivities", profile.getPreferredActivities());
        result.put("budgetPreference", profile.getBudgetPreference());
        result.put("pacePreference", profile.getPacePreference());
        result.put("favoriteCities", profile.getFavoriteCities());
        result.put("preferredSeason", profile.getPreferredSeason());
        result.put("visitCount", profile.getVisitCount());
        result.put("lastPlanTime", profile.getLastPlanTime());
        return Result.success(result);
    }

    /**
     * Manually update preferences (e.g. from a preference settings page).
     */
    @PutMapping("/preferences")
    public Result<Void> updatePreferences(@RequestHeader("Authorization") String auth,
                                          @RequestBody Map<String, Object> updates) {
        Long userId = extractUserId(auth);
        UserProfile profile = userProfileService.getOrCreateProfile(userId);

        if (updates.containsKey("preferredThemes")) {
            profile.setPreferredThemes(String.valueOf(updates.get("preferredThemes")));
        }
        if (updates.containsKey("preferredActivities")) {
            profile.setPreferredActivities(String.valueOf(updates.get("preferredActivities")));
        }
        if (updates.containsKey("budgetPreference")) {
            profile.setBudgetPreference(String.valueOf(updates.get("budgetPreference")));
        }
        if (updates.containsKey("pacePreference")) {
            profile.setPacePreference(String.valueOf(updates.get("pacePreference")));
        }
        if (updates.containsKey("favoriteCities")) {
            profile.setFavoriteCities(String.valueOf(updates.get("favoriteCities")));
        }
        if (updates.containsKey("preferredSeason")) {
            profile.setPreferredSeason(String.valueOf(updates.get("preferredSeason")));
        }
        // Note: visitCount and lastPlanTime are internally managed, not user-editable

        userProfileService.updateProfileDirectly(profile);
        return Result.success(null, "偏好设置已更新");
    }

    /**
     * Get smart default preferences derived from historical behavior.
     * Returns recommended defaults for repeat users; for new users returns sensible defaults.
     */
    @GetMapping("/recommended-defaults")
    public Result<Map<String, Object>> getRecommendedDefaults(@RequestHeader("Authorization") String auth) {
        Long userId = extractUserId(auth);
        Map<String, Object> recommendations = userProfileService.getRecommendedPreferences(userId);
        return Result.success(recommendations);
    }

    private Long extractUserId(String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                return jwtUtil.getUserId(auth.replace("Bearer ", ""));
            } catch (Exception ignored) {}
        }
        return 1L;
    }
}
