package com.travelchart.search.dto;

import com.travelchart.search.entity.PoiDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDTO {

    private long total;
    private List<PoiDocument> list;
}
