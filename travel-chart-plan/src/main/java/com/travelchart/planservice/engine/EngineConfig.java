package com.travelchart.planservice.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized engine configuration — scoring weights, city coordinate seeds, defaults.
 */
public class EngineConfig {

    // ---- Scoring Weights (must sum to ~1.0) ----

    private double diversityWeight = 0.25;
    private double proximityWeight = 0.30;
    private double budgetWeight = 0.20;
    private double themeWeight = 0.15;
    private double weatherWeight = 0.10;

    // ---- City Seed Coordinates (lat, lng) ----

    private Map<String, double[]> defaultCitySeeds = new HashMap<>();

    public EngineConfig() {
        initCitySeeds();
    }

    private void initCitySeeds() {
        defaultCitySeeds.put("北京", new double[]{39.9042, 116.4074});
        defaultCitySeeds.put("上海", new double[]{31.2304, 121.4737});
        defaultCitySeeds.put("杭州", new double[]{30.2741, 120.1551});
        defaultCitySeeds.put("成都", new double[]{30.5728, 104.0668});
        defaultCitySeeds.put("西安", new double[]{34.3416, 108.9398});
        defaultCitySeeds.put("大理", new double[]{25.6065, 100.2686});
        defaultCitySeeds.put("三亚", new double[]{18.2528, 109.5120});
    }

    public double[] getSeedCoords(String city) {
        return defaultCitySeeds.getOrDefault(city, new double[]{39.9042, 116.4074});
    }

    public boolean hasCity(String city) {
        return defaultCitySeeds.containsKey(city);
    }

    // ---- Getters / Setters ----

    public double getDiversityWeight() { return diversityWeight; }
    public void setDiversityWeight(double diversityWeight) { this.diversityWeight = diversityWeight; }

    public double getProximityWeight() { return proximityWeight; }
    public void setProximityWeight(double proximityWeight) { this.proximityWeight = proximityWeight; }

    public double getBudgetWeight() { return budgetWeight; }
    public void setBudgetWeight(double budgetWeight) { this.budgetWeight = budgetWeight; }

    public double getThemeWeight() { return themeWeight; }
    public void setThemeWeight(double themeWeight) { this.themeWeight = themeWeight; }

    public double getWeatherWeight() { return weatherWeight; }
    public void setWeatherWeight(double weatherWeight) { this.weatherWeight = weatherWeight; }

    public Map<String, double[]> getDefaultCitySeeds() { return defaultCitySeeds; }
    public void setDefaultCitySeeds(Map<String, double[]> defaultCitySeeds) { this.defaultCitySeeds = defaultCitySeeds; }
}
