package com.travelchart.search.repository;

import com.travelchart.search.entity.PoiDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PoiSearchRepository extends ElasticsearchRepository<PoiDocument, Long> {

    List<PoiDocument> findByNameOrDescription(String name, String description);
}
