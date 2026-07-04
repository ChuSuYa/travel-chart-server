package com.travelchart.planservice.engine;

import com.travelchart.common.dto.GeneratePlanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * 阶段 1：将用户的自然语言 + 结构化表单输入转换为 IntentContext。
 *
 * parseFromRequest() 将 GeneratePlanRequest 字段映射到 IntentContext。
 * parseFromFreeText() 模拟 LLM 调用，从原始文本中提取结构化意图。
 */
public class IntentParser {

    private static final Logger log = LoggerFactory.getLogger(IntentParser.class);

    private final EngineConfig config;

    public IntentParser(EngineConfig config) {
        this.config = config;
    }

    /**
     * 将结构化 API 请求转换为意图上下文
     */
    public IntentContext parseFromRequest(GeneratePlanRequest request) {
        IntentContext ctx = new IntentContext();

        if (request.getDestinations() != null) {
            ctx.setDestinations(request.getDestinations());
        }
        if (request.getDateRange() != null) {
            ctx.setDateRange(request.getDateRange());
        }
        if (request.getThemes() != null) {
            ctx.setThemes(request.getThemes());
        }
        if (request.getPace() != null && !request.getPace().isBlank()) {
            ctx.setPace(request.getPace().toLowerCase());
        } else {
            ctx.setPace("relaxed");
        }

        // 预算
        if (request.getBudget() != null) {
            ctx.setBudgetAmount(request.getBudget().getAmount());
            ctx.setBudgetLevel(request.getBudget().getLevel());
        } else {
            ctx.setBudgetAmount(7500);
            ctx.setBudgetLevel("舒适享受");
        }

        // 出行人员
        if (request.getTravelers() != null) {
            Integer a = request.getTravelers().getAdults();
            if (a != null) ctx.setTravelerAdults(a);
            Integer c = request.getTravelers().getChildren();
            if (c != null) ctx.setTravelerChildren(c);
            Integer s = request.getTravelers().getSeniors();
            if (s != null) ctx.setTravelerSeniors(s);
        }

        // 活动偏好
        if (request.getActivities() != null) {
            if (request.getActivities().getEat() != null) ctx.setActivityEat(request.getActivities().getEat());
            if (request.getActivities().getDrink() != null) ctx.setActivityDrink(request.getActivities().getDrink());
            if (request.getActivities().getPlay() != null) ctx.setActivityPlay(request.getActivities().getPlay());
            if (request.getActivities().getFun() != null) ctx.setActivityFun(request.getActivities().getFun());
        }

        // 住宿偏好
        if (request.getAccommodation() != null) {
            if (request.getAccommodation().getType() != null) ctx.setAccommodationType(request.getAccommodation().getType());
            if (request.getAccommodation().getLocation() != null) ctx.setAccommodationLocation(request.getAccommodation().getLocation());
        }

        // 自由文本
        if (request.getFreeText() != null && !request.getFreeText().isBlank()) {
            ctx.setFreeText(request.getFreeText().trim());
        }

        return ctx;
    }

    /**
     * 从自由文本解析意图（模拟 LLM 解析）
     */
    public IntentContext parseFromFreeText(String freeText) {
        IntentContext ctx = new IntentContext();
        ctx.setFreeText(freeText);

        // 简单启发式抽取
        String text = freeText.toLowerCase();

        // 目的地：抽取城市名
        String[] knownCities = {"北京", "上海", "杭州", "成都", "西安", "大理",
                "三亚", "厦门", "桂林", "重庆", "广州", "深圳", "南京", "苏州",
                "青岛", "哈尔滨", "长沙", "武汉", "昆明", "丽江"};
        List<String> found = new ArrayList<>();
        for (String city : knownCities) {
            if (text.contains(city)) {
                found.add(city);
            }
        }
        if (!found.isEmpty()) {
            ctx.setDestinations(found);
        }

        // 天数：匹配"X天"模式
        java.util.regex.Matcher dayMatcher =
                java.util.regex.Pattern.compile("(\\d+)\\s*天").matcher(text);
        if (dayMatcher.find()) {
            int days = Integer.parseInt(dayMatcher.group(1));
            ctx.setDateRange(generateDateRange(days));
        }

        // 节奏关键词
        if (text.contains("轻松") || text.contains("慢")) {
            ctx.setPace("relaxed");
        } else if (text.contains("紧凑") || text.contains("极限")) {
            ctx.setPace("compact");
        } else if (text.contains("打卡")) {
            ctx.setPace("extreme");
        } else {
            ctx.setPace("relaxed");
        }

        // 主题关键词
        if (text.contains("美食") || text.contains("吃")) {
            ctx.setThemes(Collections.singletonList("美食之旅"));
        } else if (text.contains("亲子") || text.contains("带小孩")) {
            ctx.setThemes(Collections.singletonList("亲子乐园"));
        } else if (text.contains("蜜月") || text.contains("浪漫")) {
            ctx.setThemes(Collections.singletonList("蜜月浪漫"));
        }

        // 预算关键词
        if (text.contains("省钱") || text.contains("便宜")) {
            ctx.setBudgetAmount(4000);
            ctx.setBudgetLevel("经济实惠");
        } else if (text.contains("奢华") || text.contains("高端")) {
            ctx.setBudgetAmount(25000);
            ctx.setBudgetLevel("奢华定制");
        } else {
            ctx.setBudgetAmount(7500);
            ctx.setBudgetLevel("舒适享受");
        }

        log.info("从自由文本解析出: destinations={}, days={}, pace={}",
                ctx.getDestinations(), ctx.getDateRange(), ctx.getPace());
        return ctx;
    }

    /**
     * 根据天数生成默认日期范围（从今天起）
     */
    private List<String> generateDateRange(int days) {
        LocalDate start = LocalDate.now().plusDays(7);
        LocalDate end = start.plusDays(days - 1);
        return Arrays.asList(start.toString(), end.toString());
    }
}
