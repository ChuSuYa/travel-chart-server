package com.travelchart.planservice.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * A single day's plan with ordered items.
 */
public class DailyPlan {

    private int dayNumber;
    private String date;
    private List<PlanItem> items = new ArrayList<>();
    private String weatherSummary;

    public DailyPlan() {}

    public DailyPlan(int dayNumber, String date) {
        this.dayNumber = dayNumber;
        this.date = date;
    }

    // ---- Getters / Setters ----

    public int getDayNumber() { return dayNumber; }
    public void setDayNumber(int dayNumber) { this.dayNumber = dayNumber; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public List<PlanItem> getItems() { return items; }
    public void setItems(List<PlanItem> items) { this.items = items; }

    public String getWeatherSummary() { return weatherSummary; }
    public void setWeatherSummary(String weatherSummary) { this.weatherSummary = weatherSummary; }

    public void addItem(PlanItem item) { this.items.add(item); }

    public double totalCost() {
        return items.stream().mapToDouble(PlanItem::getCost).sum();
    }

    @Override
    public String toString() {
        return "Day " + dayNumber + " (" + items.size() + " items)";
    }
}
