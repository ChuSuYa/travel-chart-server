package com.travelchart.planservice.engine;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires up the 5-stage AI plan generation engine.
 */
@Configuration
public class EngineSpringConfig {

    @Bean
    public EngineConfig engineConfig() {
        return new EngineConfig();
    }

    @Bean
    public IntentParser intentParser(EngineConfig config) {
        return new IntentParser(config);
    }

    @Bean
    public PoiRetriever poiRetriever(EngineConfig config) {
        return new PoiRetriever(config);
    }

    @Bean
    public ConstraintSolver constraintSolver(EngineConfig config, PoiRetriever retriever) {
        return new ConstraintSolver(config, retriever);
    }

    @Bean
    public RouteOptimizer routeOptimizer(EngineConfig config, PoiRetriever retriever) {
        return new RouteOptimizer(config, retriever);
    }

    @Bean
    public PlanRenderer planRenderer(EngineConfig config) {
        return new PlanRenderer(config);
    }
}
