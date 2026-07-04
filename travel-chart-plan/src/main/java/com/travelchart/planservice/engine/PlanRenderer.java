package com.travelchart.planservice.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.*;

/**
 * Stage 5: Renders structured plans into natural-language itineraries.
 *
 * Two rendering modes:
 *   - Simple: day-by-day summary with city, 3-5 highlights, budget overview, weather icon
 *   - Detailed: full daily timeline with time slots, POI details, transport guidance,
 *               meal pairing, and budget breakdown by category
 *
 * Uses template-based rendering (simulates LLM with rich templates).
 */
public class PlanRenderer {

    private static final Logger log = LoggerFactory.getLogger(PlanRenderer.class);

    private static final Map<String, String> WEATHER_ICON = Map.of(
            "spring", "☀️ 🌸",  // sun + blossom
            "summer", "☀️ 🌊",   // sun + wave
            "autumn", "🍂 ☁️",    // maple + cloud
            "winter", "❄️ ☁️"     // snow + cloud
    );

    private final EngineConfig config;
    private final NumberFormat currencyFmt;

    public PlanRenderer(EngineConfig config) {
        this.config = config;
        this.currencyFmt = NumberFormat.getCurrencyInstance(java.util.Locale.CHINA);
    }

    /**
     * Render the GeneratedPlan with both simple and detailed versions.
     */
    public GeneratedPlan render(List<DailyPlan> dailyPlans, IntentContext ctx) {
        GeneratedPlan plan = new GeneratedPlan();

        String dest = ctx.getDestinations().isEmpty() ? "多目的地" : String.join("、", ctx.getDestinations());
        plan.setDestination(dest);
        plan.setTitle(dest + " · " + String.join("/", ctx.getThemes()) + "之旅");
        plan.setThemes(ctx.getThemes());
        plan.setPace(ctx.getPace());
        plan.setDailyPlans(dailyPlans);

        double totalBudget = computeTotalBudget(dailyPlans, ctx);
        plan.setTotalBudget(Math.round(totalBudget * 100.0) / 100.0);

        plan.setSimpleVersion(renderSimple(dailyPlans, ctx));
        plan.setDetailedVersion(renderDetailed(dailyPlans, ctx));

        log.info("Rendered plan: {} days, budget ~¥{}", dailyPlans.size(), plan.getTotalBudget());
        return plan;
    }

    // ================================================================
    //  SIMPLE VERSION — day-by-day summary
    // ================================================================

    private String renderSimple(List<DailyPlan> dailyPlans, IntentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"title\": \"").append(escapeJson(ctx.getDestinations().isEmpty()
                ? "旅行计划" : String.join("、", ctx.getDestinations()) + " · "
                + String.join("/", ctx.getThemes()) + "之旅")).append("\",\n");
        sb.append("  \"pace\": \"").append(ctx.getPace()).append("\",\n");
        sb.append("  \"totalBudget\": ").append(computeTotalBudget(dailyPlans, ctx)).append(",\n");
        sb.append("  \"weatherIcon\": \"").append(WEATHER_ICON.getOrDefault(ctx.getSeason(), "")).append("\",\n");
        sb.append("  \"summary\": \"").append(escapeJson(generateSummary(dailyPlans, ctx))).append("\",\n");
        sb.append("  \"days\": [\n");

        for (int i = 0; i < dailyPlans.size(); i++) {
            DailyPlan day = dailyPlans.get(i);
            sb.append("    {\n");
            sb.append("      \"day\": ").append(day.getDayNumber()).append(",\n");
            sb.append("      \"date\": \"").append(day.getDate()).append("\",\n");
            sb.append("      \"weather\": \"").append(day.getWeatherSummary()).append("\",\n");
            sb.append("      \"highlights\": [\n");

            // 3-5 highlights
            List<PlanItem> highlightItems = day.getItems();
            int hiCount = Math.min(5, highlightItems.size());
            for (int j = 0; j < hiCount; j++) {
                PlanItem item = highlightItems.get(j);
                sb.append("        {\"name\": \"").append(escapeJson(item.getName()))
                        .append("\", \"time\": \"").append(item.getTime())
                        .append("\", \"category\": \"").append(item.getCategory()).append("\"}");
                if (j < hiCount - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("      ],\n");
            sb.append("      \"dayBudget\": ").append(Math.round(day.totalCost() * 100.0) / 100.0).append("\n");
            sb.append("    }");
            if (i < dailyPlans.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    // ================================================================
    //  DETAILED VERSION — full timeline
    // ================================================================

    private String renderDetailed(List<DailyPlan> dailyPlans, IntentContext ctx) {
        StringBuilder sb = new StringBuilder();
        double totalBudget = computeTotalBudget(dailyPlans, ctx);

        sb.append("{\n");
        sb.append("  \"title\": \"").append(escapeJson(ctx.getDestinations().isEmpty()
                ? "旅行计划" : String.join("、", ctx.getDestinations()) + " · "
                + String.join("/", ctx.getThemes()) + "之旅")).append("\",\n");
        sb.append("  \"destination\": \"").append(escapeJson(ctx.getDestinations().isEmpty()
                ? "多目的地" : String.join("、", ctx.getDestinations()))).append("\",\n");
        sb.append("  \"themes\": [");
        for (int t = 0; t < ctx.getThemes().size(); t++) {
            sb.append("\"").append(ctx.getThemes().get(t)).append("\"");
            if (t < ctx.getThemes().size() - 1) sb.append(", ");
        }
        sb.append("],\n");
        sb.append("  \"pace\": \"").append(ctx.getPace()).append("\",\n");
        sb.append("  \"season\": \"").append(ctx.getSeason()).append("\",\n");
        sb.append("  \"totalBudget\": ").append(totalBudget).append(",\n");
        sb.append("  \"travelers\": {\n");
        sb.append("    \"adults\": ").append(ctx.getTravelerAdults()).append(",\n");
        sb.append("    \"children\": ").append(ctx.getTravelerChildren()).append(",\n");
        sb.append("    \"seniors\": ").append(ctx.getTravelerSeniors()).append("\n");
        sb.append("  },\n");

        // Budget breakdown
        sb.append("  \"budgetBreakdown\": {\n");
        double meals = dailyPlans.stream()
                .flatMap(d -> d.getItems().stream())
                .filter(i -> "餐饮".equals(i.getCategory()))
                .mapToDouble(PlanItem::getCost).sum();
        double attractions = dailyPlans.stream()
                .flatMap(d -> d.getItems().stream())
                .filter(i -> "景点".equals(i.getCategory()) || "娱乐".equals(i.getCategory()))
                .mapToDouble(PlanItem::getCost).sum();
        double transport = dailyPlans.stream()
                .flatMap(d -> d.getItems().stream())
                .filter(i -> "交通".equals(i.getCategory()))
                .mapToDouble(PlanItem::getCost).sum();
        double accommodation = totalBudget - meals - attractions - transport;
        sb.append("    \"meals\": ").append(Math.round(meals * 100.0) / 100.0).append(",\n");
        sb.append("    \"attractions\": ").append(Math.round(attractions * 100.0) / 100.0).append(",\n");
        sb.append("    \"transport\": ").append(Math.round(transport * 100.0) / 100.0).append(",\n");
        sb.append("    \"accommodation\": ").append(Math.round(Math.max(0, accommodation) * 100.0) / 100.0).append("\n");
        sb.append("  },\n");

        sb.append("  \"dailyTimeline\": [\n");

        for (int i = 0; i < dailyPlans.size(); i++) {
            DailyPlan day = dailyPlans.get(i);
            sb.append("    {\n");
            sb.append("      \"dayNumber\": ").append(day.getDayNumber()).append(",\n");
            sb.append("      \"date\": \"").append(day.getDate()).append("\",\n");
            sb.append("      \"weather\": \"").append(day.getWeatherSummary()).append("\",\n");
            sb.append("      \"dayBudget\": ").append(Math.round(day.totalCost() * 100.0) / 100.0).append(",\n");
            sb.append("      \"itinerary\": [\n");

            List<PlanItem> items = day.getItems();
            for (int j = 0; j < items.size(); j++) {
                PlanItem item = items.get(j);
                sb.append("        {\n");
                sb.append("          \"time\": \"").append(item.getTime()).append("\",\n");
                sb.append("          \"name\": \"").append(escapeJson(item.getName())).append("\",\n");
                sb.append("          \"description\": \"").append(escapeJson(item.getDescription())).append("\",\n");
                sb.append("          \"category\": \"").append(item.getCategory()).append("\",\n");
                sb.append("          \"durationMinutes\": ").append(item.getDurationMinutes()).append(",\n");
                sb.append("          \"cost\": ").append(item.getCost()).append(",\n");
                sb.append("          \"lat\": ").append(item.getLat()).append(",\n");
                sb.append("          \"lng\": ").append(item.getLng()).append(",\n");
                sb.append("          \"tips\": \"").append(escapeJson(item.getTips())).append("\"\n");
                sb.append("        }");
                if (j < items.size() - 1) sb.append(",");
                sb.append("\n");
            }

            sb.append("      ]\n");
            sb.append("    }");
            if (i < dailyPlans.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    // ---- Helpers ----

    private String generateSummary(List<DailyPlan> dailyPlans, IntentContext ctx) {
        int totalPOIs = dailyPlans.stream().mapToInt(d -> d.getItems().size()).sum();
        long uniqueCities = dailyPlans.stream()
                .flatMap(d -> d.getItems().stream())
                .map(PlanItem::getName)
                .distinct().count();

        String paceLabel = switch (ctx.getPace()) {
            case "intensive" -> "紧凑充实型";
            case "compact" -> "适中饱满型";
            default -> "轻松惬意型";
        };

        return String.format("共%d天%d个景点，覆盖%d个城市，%s行程",
                dailyPlans.size(), totalPOIs, uniqueCities, paceLabel);
    }

    private double computeTotalBudget(List<DailyPlan> dailyPlans, IntentContext ctx) {
        double total = dailyPlans.stream().mapToDouble(DailyPlan::totalCost).sum();
        if (ctx.getBudgetAmount() != null && ctx.getBudgetAmount() > 0) {
            // Scale to fit within budget if over
            return Math.min(total, ctx.getBudgetAmount());
        }
        return total;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Generate a human-readable text summary (for display purposes).
     */
    public String renderTextSummary(GeneratedPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(plan.getTitle()).append(" ===\n\n");
        sb.append("节奏: ").append(plan.getPace()).append(" | 预算: ¥").append(plan.getTotalBudget()).append("\n\n");

        for (DailyPlan day : plan.getDailyPlans()) {
            sb.append("--- Day ").append(day.getDayNumber())
                    .append(" (").append(day.getDate()).append(") ---\n");
            sb.append("天气: ").append(day.getWeatherSummary()).append("\n");
            for (PlanItem item : day.getItems()) {
                sb.append(String.format("  %s  %-20s  [%s]  ¥%.0f\n",
                        item.getTime(), item.getName(), item.getCategory(), item.getCost()));
            }
            sb.append("\n");
        }
        sb.append("总预算: ¥").append(plan.getTotalBudget()).append("\n");
        return sb.toString();
    }
}
