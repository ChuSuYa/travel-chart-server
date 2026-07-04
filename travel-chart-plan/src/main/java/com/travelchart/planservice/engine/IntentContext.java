package com.travelchart.planservice.engine;

import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured intent extracted from user request — drives the entire planning pipeline.
 */
@Setter
public class IntentContext {

    private List<String> destinations = new ArrayList<>();
    private List<String> dateRange = new ArrayList<>();
    private int totalDays;

    private List<String> themes = new ArrayList<>();
    private String pace = "relaxed";

    private Integer budgetAmount;
    private String budgetLevel;

    private int travelerAdults = 1;
    private int travelerChildren;
    private int travelerSeniors;

    private List<String> activityEat = new ArrayList<>();
    private List<String> activityDrink = new ArrayList<>();
    private List<String> activityPlay = new ArrayList<>();
    private List<String> activityFun = new ArrayList<>();

    private String freeText;

    private String accommodationType;
    private String accommodationLocation;

    private String season;

    public IntentContext() {}

    // ---- Convenience builders ----

    public int getTotalDays() {
        if (totalDays > 0) return totalDays;
        if (dateRange != null && dateRange.size() >= 2) {
            // very rough: count comma-separated YYYY-MM-DD keys
            return dateRange.size() >= 2 ? Math.max(1, estimateDays(dateRange.get(0), dateRange.get(1))) : 1;
        }
        return 1;
    }

    private int estimateDays(String start, String end) {
        try {
            java.time.LocalDate s = java.time.LocalDate.parse(start);
            java.time.LocalDate e = java.time.LocalDate.parse(end);
            return (int) java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1;
        } catch (Exception ex) { return 1; }
    }

    public int getPoiCountTarget() {
        switch (pace) {
            case "intensive": return 7;
            case "compact": return 5;
            default: return 4; // relaxed
        }
    }

    // ---- Getters / Setters (Lombok-free for clarity) ----

    public List<String> getDestinations() { return destinations; }

    public List<String> getDateRange() { return dateRange; }

    public List<String> getThemes() { return themes; }

    public String getPace() { return pace; }

    public Integer getBudgetAmount() { return budgetAmount; }

    public String getBudgetLevel() { return budgetLevel; }

    public int getTravelerAdults() { return travelerAdults; }

    public int getTravelerChildren() { return travelerChildren; }

    public int getTravelerSeniors() { return travelerSeniors; }

    public List<String> getActivityEat() { return activityEat; }

    public List<String> getActivityDrink() { return activityDrink; }

    public List<String> getActivityPlay() { return activityPlay; }

    public List<String> getActivityFun() { return activityFun; }

    public String getFreeText() { return freeText; }

    public String getAccommodationType() { return accommodationType; }

    public String getAccommodationLocation() { return accommodationLocation; }

    public String getSeason() { return season; }

    public boolean hasSeniors() { return travelerSeniors > 0; }
    public boolean hasChildren() { return travelerChildren > 0; }
    public boolean isSoloTraveler() { return travelerAdults == 1 && travelerChildren == 0 && travelerSeniors == 0; }

    @Override
    public String toString() {
        return "IntentContext{dest=" + destinations + ", themes=" + themes + ", pace=" + pace + "}";
    }
}
