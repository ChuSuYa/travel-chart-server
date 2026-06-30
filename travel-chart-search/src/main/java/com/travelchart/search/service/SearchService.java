package com.travelchart.search.service;

import com.travelchart.search.dto.SearchResultDTO;
import com.travelchart.search.entity.PoiDocument;
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

@Service
public class SearchService {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    public SearchResultDTO search(String keyword, String city, Double lat, Double lon, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);

        // multi_match: name^3 + description
        MultiMatchQueryBuilder multiMatch = QueryBuilders.multiMatchQuery(keyword)
                .field("name", 3.0f)
                .field("description", 1.0f);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery().must(multiMatch);

        // city filter
        if (StringUtils.hasText(city)) {
            boolQuery.filter(QueryBuilders.termQuery("city", city));
        }

        // function_score: heatScore field_value_factor + time gauss
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] functionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.fieldValueFactorFunction("heatScore")
                                .factor(1.2f)
                                .modifier(org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier.LOG1P)
                                .missing(1.0)
                ),
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.gaussDecayFunction("createTime", "now", "30d", "7d", 0.5)
                )
        };

        // location gauss decay (only if lat/lon provided)
        if (lat != null && lon != null) {
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] extended =
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder[functionBuilders.length + 1];
            System.arraycopy(functionBuilders, 0, extended, 0, functionBuilders.length);
            extended[functionBuilders.length] = new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                    ScoreFunctionBuilders.gaussDecayFunction("location",
                            new org.elasticsearch.common.geo.GeoPoint(lat, lon), "5km", "0km", 0.5)
            );
            functionBuilders = extended;
        }

        FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(boolQuery, functionBuilders)
                .scoreMode(org.elasticsearch.common.lucene.search.function.FunctionScoreQuery.ScoreMode.SUM)
                .boostMode(org.elasticsearch.common.lucene.search.function.CombineFunction.SUM);

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
}
