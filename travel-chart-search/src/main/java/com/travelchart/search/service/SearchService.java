package com.travelchart.search.service;

import com.travelchart.search.dto.SearchResultDTO;
import com.travelchart.search.entity.PoiDocument;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced search service with multi-lingual support, location-based ranking,
 * category filtering, hot words, and autocomplete suggestions.
 */
@Service
public class SearchService {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    private HotWordsService hotWordsService;

    // ================================================================
    //  Core search (backward compatible)
    // ================================================================

    public SearchResultDTO search(String keyword, String city, Double lat, Double lon, int page, int size) {
        return searchPoi(keyword, city, null, null, null, null, null, lat, lon, page, size);
    }

    // ================================================================
    //  Enhanced search with full filters
    // ================================================================

    /**
     * Enhanced POI search with:
     *   - Multi-lingual support (Chinese ik_max_word + ik_smart; English match)
     *   - Location-based ranking via function_score
     *   - Filter by type, price, indoor/outdoor, seasonality
     */
    public SearchResultDTO searchPoi(String keyword, String city, String type,
                                     Double priceMin, Double priceMax,
                                     Boolean indoor, String season,
                                     Double lat, Double lng,
                                     int page, int size) {
        int pageIndex = Math.max(page - 1, 0);

        // Record search keyword for hot words tracking
        if (StringUtils.hasText(keyword)) {
            hotWordsService.recordSearch(keyword);
        }

        // ---- Build main query ----
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        if (StringUtils.hasText(keyword)) {
            boolQuery.must(buildMultiLingualQuery(keyword));
        } else {
            boolQuery.must(QueryBuilders.matchAllQuery());
        }

        // ---- Filters ----
        if (StringUtils.hasText(city)) {
            boolQuery.filter(QueryBuilders.termQuery("city", city));
        }

        if (StringUtils.hasText(type)) {
            boolQuery.filter(QueryBuilders.termQuery("type", type));
        }

        if (priceMin != null || priceMax != null) {
            if (priceMin != null && priceMax != null) {
                boolQuery.filter(QueryBuilders.rangeQuery("price").gte(priceMin).lte(priceMax));
            } else if (priceMin != null) {
                boolQuery.filter(QueryBuilders.rangeQuery("price").gte(priceMin));
            } else {
                boolQuery.filter(QueryBuilders.rangeQuery("price").lte(priceMax));
            }
        }

        if (indoor != null) {
            boolQuery.filter(QueryBuilders.termQuery("indoor", indoor));
        }

        if (StringUtils.hasText(season)) {
            boolQuery.filter(QueryBuilders.termQuery("season", season));
        }

        // ---- Function score: heatScore + time decay + location decay ----
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] functionBuilders = buildFunctionScore(lat, lng);

        FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(boolQuery, functionBuilders)
                .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                .boostMode(CombineFunction.SUM);

        // ---- Execute ----
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(functionScoreQuery)
                .withPageable(PageRequest.of(pageIndex, size))
                .build();

        SearchHits<PoiDocument> searchHits = elasticsearchRestTemplate.search(searchQuery, PoiDocument.class);

        long total = searchHits.getTotalHits();
        List<PoiDocument> list = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        return new SearchResultDTO(total, list);
    }

    // ================================================================
    //  Hot words
    // ================================================================

    /**
     * Get trending search keywords with scores from Redis.
     */
    public List<String> getHotWords() {
        return hotWordsService.getTopHotWords(10).stream()
                .map(m -> (String) m.get("keyword"))
                .collect(Collectors.toList());
    }

    /**
     * Get hot words with scores (for richer UIs).
     */
    public List<java.util.Map<String, Object>> getHotWordsWithScores(int limit) {
        return hotWordsService.getTopHotWords(limit);
    }

    // ================================================================
    //  Autocomplete suggestions
    // ================================================================

    /**
     * Get autocomplete suggestions for a given prefix.
     * Uses Redis sorted set for top-K prefix matching.
     * Falls back to ES prefix query or completion suggester for production.
     */
    public List<String> getSuggestions(String prefix, String city) {
        List<String> suggestions = hotWordsService.getSuggestions(prefix, 8);

        // If city is provided, also prepend city-specific suggestions
        if (StringUtils.hasText(city)) {
            String cityPrefix = city + prefix;
            List<String> citySuggestions = hotWordsService.getSuggestions(cityPrefix, 3);
            for (String s : citySuggestions) {
                if (!suggestions.contains(s)) {
                    suggestions.add(0, s);
                }
            }
            // Trim to max 8
            if (suggestions.size() > 8) {
                suggestions = suggestions.subList(0, 8);
            }
        }

        return suggestions;
    }

    // ================================================================
    //  Private helpers
    // ================================================================

    /**
     * Build a multi-lingual query:
     * - If keyword contains CJK characters, use ik_max_word for name + ik_smart for description
     * - If purely ASCII/English, use standard analyzer for both
     */
    private MultiMatchQueryBuilder buildMultiLingualQuery(String keyword) {
        boolean isChinese = containsCJK(keyword);

        MultiMatchQueryBuilder multiMatch = QueryBuilders.multiMatchQuery(keyword)
                .field("name", 3.0f)
                .field("description", 1.0f);

        if (isChinese) {
            multiMatch.analyzer("ik_smart");
        }
        // For English, default standard analyzer is sufficient

        return multiMatch;
    }

    /**
     * Build function_score filters: heatScore field_value_factor + time decay + optional location decay.
     */
    private FunctionScoreQueryBuilder.FilterFunctionBuilder[] buildFunctionScore(Double lat, Double lng) {
        FunctionScoreQueryBuilder.FilterFunctionBuilder heatScoreFn =
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.fieldValueFactorFunction("heatScore")
                                .factor(1.2f)
                                .modifier(FieldValueFactorFunction.Modifier.LOG1P)
                                .missing(1.0)
                );

        FunctionScoreQueryBuilder.FilterFunctionBuilder timeDecayFn =
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.gaussDecayFunction("createTime", "now", "30d", "7d", 0.5)
                );

        if (lat != null && lng != null) {
            return new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                    heatScoreFn,
                    timeDecayFn,
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            ScoreFunctionBuilders.gaussDecayFunction("location",
                                    new org.elasticsearch.common.geo.GeoPoint(lat, lng),
                                    "5km", "0km", 0.5)
                    )
            };
        }

        return new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                heatScoreFn,
                timeDecayFn
        };
    }

    /**
     * Check if a string contains CJK (Chinese/Japanese/Korean) characters.
     */
    private boolean containsCJK(String text) {
        if (text == null) return false;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                return true;
            }
            // Also check Unicode script for broader CJK coverage
            Character.UnicodeScript script = Character.UnicodeScript.of(c);
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
