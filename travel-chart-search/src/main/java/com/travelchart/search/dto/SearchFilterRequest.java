package com.travelchart.search.dto;

import lombok.Data;

/**
 * Search filter parameters for the /api/search/filter endpoint.
 * All fields are optional and fall back to default search behavior when omitted.
 */
@Data
public class SearchFilterRequest {

    /** Search keyword */
    private String keyword;

    /** City filter */
    private String city;

    /** POI type: food, drink, play, fun, hotel, transport */
    private String type;

    /** Minimum price */
    private Double priceMin;

    /** Maximum price */
    private Double priceMax;

    /** Indoor filter: true = indoor only, false = outdoor only */
    private Boolean indoor;

    /** Best season: spring, summer, autumn, winter, all_year */
    private String season;

    /** Latitude for location-based ranking */
    private Double lat;

    /** Longitude for location-based ranking */
    private Double lng;

    /** Page number (1-based) */
    private Integer page = 1;

    /** Page size */
    private Integer size = 20;
}
