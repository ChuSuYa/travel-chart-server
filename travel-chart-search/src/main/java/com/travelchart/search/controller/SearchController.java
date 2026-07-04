package com.travelchart.search.controller;

import com.travelchart.common.result.Result;
import com.travelchart.search.dto.SearchFilterRequest;
import com.travelchart.search.dto.SearchResultDTO;
import com.travelchart.search.service.HotWordsService;
import com.travelchart.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Search API controller.
 *
 * Endpoints:
 *   GET /api/search/poi         — basic search (backward compatible)
 *   GET /api/search/filter      — advanced search with type/price/indoor/season filters
 *   GET /api/search/hot-words   — trending search keywords from Redis
 *   GET /api/search/suggest     — autocomplete suggestions
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @Autowired
    private HotWordsService hotWordsService;

    // ================================================================
    //  POI search (backward compatible)
    // ================================================================

    /**
     * Basic POI search. Kept for backward compatibility.
     */
    @GetMapping("/poi")
    public Result<SearchResultDTO> searchPoi(
            @RequestParam String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return Result.success(searchService.search(keyword, city, lat, lon, page, size));
    }

    // ================================================================
    //  Advanced filter search
    // ================================================================

    /**
     * Enhanced search with full filter support.
     * All filter params are optional and fall back to existing search behavior.
     *
     * Query params:
     *   keyword    — search keyword (supports Chinese & English)
     *   city       — city filter
     *   type       — POI type: food, drink, play, fun, hotel, transport
     *   priceMin   — minimum price
     *   priceMax   — maximum price
     *   indoor     — true=indoor only, false=outdoor only
     *   season     — best season: spring, summer, autumn, winter, all_year
     *   lat, lng   — location for proximity ranking
     *   page, size — pagination
     */
    @GetMapping("/filter")
    public Result<SearchResultDTO> filterSearch(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Double priceMin,
            @RequestParam(required = false) Double priceMax,
            @RequestParam(required = false) Boolean indoor,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return Result.success(searchService.searchPoi(
                keyword, city, type, priceMin, priceMax, indoor, season,
                lat, lng, page, size));
    }

    // ================================================================
    //  Hot words
    // ================================================================

    /**
     * Get trending search keywords.
     * Powered by Redis sorted set with 7-day TTL.
     */
    @GetMapping("/hot-words")
    public Result<List<String>> hotWords() {
        return Result.success(searchService.getHotWords());
    }

    /**
     * Get hot words with scores for richer UI display.
     */
    @GetMapping("/hot-words/detail")
    public Result<List<Map<String, Object>>> hotWordsDetail(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(searchService.getHotWordsWithScores(limit));
    }

    // ================================================================
    //  Autocomplete suggestions
    // ================================================================

    /**
     * Get autocomplete suggestions for a given prefix.
     * Uses Redis sorted set for top-K prefix matching.
     */
    @GetMapping("/suggest")
    public Result<List<String>> suggest(
            @RequestParam String prefix,
            @RequestParam(required = false) String city) {
        return Result.success(searchService.getSuggestions(prefix, city));
    }
}
