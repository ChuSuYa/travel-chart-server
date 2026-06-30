package com.travelchart.planservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class GeneratePlanRequest {
    private List<String> destinations;
    private List<String> dateRange;
    private Travelers travelers;
    private String relationTag;
    private Budget budget;
    private List<String> themes;
    private Activities activities;
    private String pace;
    private Accommodation accommodation;

    @Data
    public static class Travelers {
        private Integer adults;
        private Integer children;
        private Integer seniors;
    }

    @Data
    public static class Budget {
        private Integer amount;
        private String level;
    }

    @Data
    public static class Activities {
        private List<String> eat;
        private List<String> drink;
        private List<String> play;
        private List<String> fun;
    }

    @Data
    public static class Accommodation {
        private String type;
        private String location;
    }
}
