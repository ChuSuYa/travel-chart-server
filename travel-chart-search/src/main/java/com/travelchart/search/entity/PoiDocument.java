package com.travelchart.search.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.util.Date;
import java.util.List;

@Document(indexName = "travel_poi")
@Data
public class PoiDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_smart")
    private String description;

    @Field(type = FieldType.Keyword)
    private String city;

    @GeoPointField
    private GeoPoint location;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Double)
    private Double rating;

    @Field(type = FieldType.Double)
    private Double heatScore;

    @Field(type = FieldType.Keyword)
    private String imageUrl;

    @Field(type = FieldType.Keyword)
    private String type;

    /** Whether this POI is primarily indoor: true=indoor, false=outdoor, null=unknown */
    @Field(type = FieldType.Boolean)
    private Boolean indoor;

    /** Best season to visit, e.g. "spring", "summer", "autumn", "winter", "all_year" */
    @Field(type = FieldType.Keyword)
    private String season;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private Date createTime;
}
