package com.travelchart.search.controller;

import com.travelchart.common.result.Result;
import com.travelchart.search.dto.SearchResultDTO;
import com.travelchart.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private SearchService searchService;

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

    @GetMapping("/hot-words")
    public Result<List<String>> hotWords() {
        // TODO: 接入 Redis 搜索日志统计后实现
        return Result.success(List.of("西湖", "长城", "黄山", "三亚", "九寨沟"));
    }

    @GetMapping("/suggest")
    public Result<List<String>> suggest(@RequestParam String prefix) {
        // TODO: 接入 ES Completion Suggester 后实现
        return Result.success(List.of());
    }
}
