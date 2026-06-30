package com.travelchart.search.controller;

import com.travelchart.common.result.Result;
import com.travelchart.search.entity.PoiDocument;
import com.travelchart.search.service.EsSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feign/sync")
public class SyncController {

    @Autowired
    private EsSyncService esSyncService;

    /**
     * OpenFeign: 单条同步 POI 到 ES
     */
    @PostMapping("/poi")
    public Result<Void> syncPoi(@RequestBody PoiDocument doc) {
        esSyncService.syncPoiToEs(doc);
        return Result.success(null);
    }

    /**
     * OpenFeign: 批量同步 POI 到 ES
     */
    @PostMapping("/batch")
    public Result<Void> batchSyncPois(@RequestBody List<PoiDocument> docs) {
        esSyncService.batchSyncPois(docs);
        return Result.success(null);
    }
}
