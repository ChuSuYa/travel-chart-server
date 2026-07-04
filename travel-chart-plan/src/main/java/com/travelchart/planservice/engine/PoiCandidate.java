package com.travelchart.planservice.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * A candidate POI from the knowledge base — used by PoiRetriever and the solver.
 */
public class PoiCandidate {

    private String name;
    private String city;
    private String type;        // e.g. 景点, 餐饮, 购物, 娱乐
    private String subType;     // e.g. 博物馆, 公园, 火锅, 酒吧
    private double lat;
    private double lng;
    private double rating;      // 1.0 - 5.0
    private int priceLevel;     // 1=budget, 2=moderate, 3=premium
    private String openingHours;
    private int durationMinutes;
    private boolean indoor;
    private List<String> tags = new ArrayList<>();
    private List<String> seasonality = new ArrayList<>(); // e.g. ["spring","autumn"]

    public PoiCandidate() {}

    public PoiCandidate(String name, String city, String type, String subType,
                        double lat, double lng, double rating, int priceLevel,
                        String openingHours, int durationMinutes, boolean indoor) {
        this.name = name;
        this.city = city;
        this.type = type;
        this.subType = subType;
        this.lat = lat;
        this.lng = lng;
        this.rating = rating;
        this.priceLevel = priceLevel;
        this.openingHours = openingHours;
        this.durationMinutes = durationMinutes;
        this.indoor = indoor;
    }

    // ---- Getters / Setters ----

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSubType() { return subType; }
    public void setSubType(String subType) { this.subType = subType; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public int getPriceLevel() { return priceLevel; }
    public void setPriceLevel(int priceLevel) { this.priceLevel = priceLevel; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public boolean isIndoor() { return indoor; }
    public void setIndoor(boolean indoor) { this.indoor = indoor; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getSeasonality() { return seasonality; }
    public void setSeasonality(List<String> seasonality) { this.seasonality = seasonality; }

    /**
     * Haversine distance in km to another POI.
     */
    public double distanceKmTo(PoiCandidate other) {
        return distanceKmTo(other.lat, other.lng);
    }

    public double distanceKmTo(double lat2, double lng2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - this.lat);
        double dLng = Math.toRadians(lng2 - this.lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(this.lat)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @Override
    public String toString() {
        return "PoiCandidate{" + name + " (" + city + ") " + type + "/" + subType + " rating=" + rating + "}";
    }
}
