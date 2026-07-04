package com.travelchart.userservice.client;

import com.travelchart.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "travel-chart-search")
public interface SearchFeignClient {

    @PostMapping("/api/feign/sync/poi")
    Result<Void> syncPoi(@RequestBody PoiDocument doc);

    @PostMapping("/api/feign/sync/batch")
    Result<Void> batchSyncPois(@RequestBody List<PoiDocument> docs);
}
