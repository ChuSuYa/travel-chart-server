package com.travelchart.search.service;

import com.travelchart.search.entity.PoiDocument;
import com.travelchart.search.repository.PoiSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EsSyncService {

    @Autowired
    private PoiSearchRepository poiSearchRepository;

    /**
     * 单条同步 POI 到 ES
     */
    public void syncPoiToEs(PoiDocument doc) {
        poiSearchRepository.save(doc);
    }

    /**
     * 批量同步 POI 到 ES
     */
    public void batchSyncPois(List<PoiDocument> docs) {
        if (docs != null && !docs.isEmpty()) {
            poiSearchRepository.saveAll(docs);
        }
    }
}
