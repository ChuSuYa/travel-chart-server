package com.travelchart.planservice.engine;

/**
 * A single time-slot item within a day's itinerary.
 */
public class PlanItem {

    private String time;        // "09:00"
    private String name;
    private String description;
    private String category;    // 景点, 餐饮, 交通, 住宿
    private int durationMinutes;
    private double cost;
    private double lat;
    private double lng;
    private String tips;

    public PlanItem() {}

    public PlanItem(String time, String name, String description, String category,
                    int durationMinutes, double cost, double lat, double lng, String tips) {
        this.time = time;
        this.name = name;
        this.description = description;
        this.category = category;
        this.durationMinutes = durationMinutes;
        this.cost = cost;
        this.lat = lat;
        this.lng = lng;
        this.tips = tips;
    }

    // ---- Getters / Setters ----

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public String getTips() { return tips; }
    public void setTips(String tips) { this.tips = tips; }

    @Override
    public String toString() {
        return time + " " + name + " [" + category + "]";
    }
}
