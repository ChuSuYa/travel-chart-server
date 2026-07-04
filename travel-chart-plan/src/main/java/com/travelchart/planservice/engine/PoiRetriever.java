package com.travelchart.planservice.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Stage 2: Retrieves candidate POIs from the knowledge base using multi-dimensional filtering.
 *
 * Maps theme keywords to POI types. Contains seed data for 40+ POIs across 7 Chinese cities
 * with realistic Chinese names, coordinates, ratings, prices, and opening hours.
 */
public class PoiRetriever {

    private static final Logger log = LoggerFactory.getLogger(PoiRetriever.class);

    private final EngineConfig config;
    private final List<PoiCandidate> allPOIs = new ArrayList<>();

    // Theme → preferred subType keywords map
    private static final Map<String, List<String>> THEME_TYPE_MAP = new HashMap<>();
    static {
        THEME_TYPE_MAP.put("history",    List.of("博物馆", "古迹", "寺庙", "遗址", "宫殿", "城墙", "园林", "陵墓"));
        THEME_TYPE_MAP.put("food",       List.of("餐厅", "小吃街", "火锅", "烤鸭", "面馆", "夜市", "茶馆", "海鲜"));
        THEME_TYPE_MAP.put("outdoor",    List.of("公园", "山", "湖", "海滩", "峡谷", "徒步道", "自然保护", "花园"));
        THEME_TYPE_MAP.put("shopping",   List.of("商场", "步行街", "市场", "特产店", "免税店"));
        THEME_TYPE_MAP.put("family",     List.of("乐园", "动物园", "水族馆", "科技馆", "植物园", "公园"));
        THEME_TYPE_MAP.put("romantic",   List.of("夜景", "湖畔", "江景", "花园", "日落", "温泉"));
        THEME_TYPE_MAP.put("photography",List.of("古镇", "街道", "观景台", "大桥", "建筑", "涂鸦"));
        THEME_TYPE_MAP.put("adventure",  List.of("峡谷", "漂流", "潜水", "徒步道", "攀岩", "滑雪"));
    }

    public PoiRetriever(EngineConfig config) {
        this.config = config;
        initSeedData();
    }

    /**
     * Retrieve POIs filtered by the user's intent.
     */
    public List<PoiCandidate> retrieve(IntentContext ctx) {
        List<PoiCandidate> candidates = new ArrayList<>();

        // Filter by destination city
        for (String dest : ctx.getDestinations()) {
            List<PoiCandidate> cityPOIs = allPOIs.stream()
                    .filter(p -> p.getCity().equals(dest))
                    .collect(Collectors.toList());
            candidates.addAll(cityPOIs);
        }

        // If no city match, return all as fallback
        if (candidates.isEmpty()) {
            candidates.addAll(allPOIs);
        }

        // Boost POIs matching the user's themes
        List<String> preferredSubTypes = new ArrayList<>();
        for (String theme : ctx.getThemes()) {
            List<String> st = THEME_TYPE_MAP.getOrDefault(theme, Collections.emptyList());
            preferredSubTypes.addAll(st);
        }

        // Score each candidate
        List<PoiCandidate> scored = new ArrayList<>();
        for (PoiCandidate p : candidates) {
            double score = 0.0;

            // Theme match
            for (String st : preferredSubTypes) {
                if (p.getSubType() != null && p.getSubType().contains(st)) score += 3.0;
                if (p.getTags().contains(st)) score += 2.0;
            }
            // Rating boost
            score += p.getRating() * 0.5;
            // Season match
            if (p.getSeasonality().contains(ctx.getSeason())) score += 2.0;
            // Indoor preference for bad weather (winter/rainy)
            if ("winter".equals(ctx.getSeason()) && p.isIndoor()) score += 1.5;
            // Family friendly
            if (ctx.hasChildren() && p.getTags().contains("亲子")) score += 3.0;
            if (ctx.hasSeniors() && p.isIndoor()) score += 1.0; // seniors prefer indoor

            // Store score in a temporary field (we use tags to smuggle it)
            PoiCandidate copy = copyPoi(p);
            // Use a dummy approach: add score as a searchable tag
            copy.getTags().add("__score__" + (int)(score * 10));
            scored.add(copy);
        }

        // Sort by score descending, then rating
        scored.sort((a, b) -> {
            double sa = extractScore(a);
            double sb = extractScore(b);
            return Double.compare(sb, sa);
        });

        log.info("Retrieved {} candidates from {} total for intent themes={}",
                scored.size(), candidates.size(), ctx.getThemes());
        return scored;
    }

    /**
     * Retrieve all POIs for a given city (unfiltered).
     */
    public List<PoiCandidate> getByCity(String city) {
        return allPOIs.stream().filter(p -> p.getCity().equals(city)).collect(Collectors.toList());
    }

    /**
     * Get POIs of a specific type in a city.
     */
    public List<PoiCandidate> getByCityAndType(String city, String type) {
        return allPOIs.stream()
                .filter(p -> p.getCity().equals(city) && p.getType().equals(type))
                .collect(Collectors.toList());
    }

    /**
     * Find restaurants near a given location, within radiusKm.
     */
    public List<PoiCandidate> findNearbyRestaurants(double lat, double lng, double radiusKm) {
        return allPOIs.stream()
                .filter(p -> "餐饮".equals(p.getType()))
                .filter(p -> p.distanceKmTo(lat, lng) <= radiusKm)
                .sorted(Comparator.comparingDouble(a -> a.distanceKmTo(lat, lng)))
                .collect(Collectors.toList());
    }

    /**
     * Find all POIs near a given location, within radiusKm, optionally filtered by type.
     */
    public List<PoiCandidate> findNearby(double lat, double lng, double radiusKm, String type) {
        return allPOIs.stream()
                .filter(p -> type == null || p.getType().equals(type) || "餐饮".equals(p.getType()))
                .filter(p -> p.distanceKmTo(lat, lng) <= radiusKm)
                .sorted(Comparator.comparingDouble(a -> a.distanceKmTo(lat, lng)))
                .collect(Collectors.toList());
    }

    public int totalPoiCount() { return allPOIs.size(); }

    // ---- Helpers ----

    private double extractScore(PoiCandidate p) {
        for (String tag : p.getTags()) {
            if (tag.startsWith("__score__")) {
                try { return Double.parseDouble(tag.substring(9)); } catch (Exception e) { return 0; }
            }
        }
        return 0;
    }

    private PoiCandidate copyPoi(PoiCandidate src) {
        PoiCandidate p = new PoiCandidate();
        p.setName(src.getName());
        p.setCity(src.getCity());
        p.setType(src.getType());
        p.setSubType(src.getSubType());
        p.setLat(src.getLat());
        p.setLng(src.getLng());
        p.setRating(src.getRating());
        p.setPriceLevel(src.getPriceLevel());
        p.setOpeningHours(src.getOpeningHours());
        p.setDurationMinutes(src.getDurationMinutes());
        p.setIndoor(src.isIndoor());
        p.setTags(new ArrayList<>(src.getTags()));
        p.setSeasonality(new ArrayList<>(src.getSeasonality()));
        return p;
    }

    // ======================================================================
    //  SEED DATA — 40+ POIs across 7 cities with real Chinese names
    // ======================================================================

    private void initSeedData() {
        // ----- 北京 (Beijing) -----
        addPoi("故宫博物院", "北京", "景点", "博物馆/宫殿", 39.9163, 116.3972, 4.9, 3,
                "08:30-17:00", 180, false, List.of("世界遗产", "亲子"),
                List.of("spring","autumn","winter"));
        addPoi("天坛公园", "北京", "景点", "公园/古迹", 39.8822, 116.4066, 4.7, 2,
                "06:00-21:00", 120, false, List.of("世界遗产", "摄影"),
                List.of("spring","autumn","summer"));
        addPoi("颐和园", "北京", "景点", "园林/湖", 39.9999, 116.2755, 4.8, 2,
                "07:00-17:00", 150, false, List.of("世界遗产", "亲子"),
                List.of("spring","autumn"));
        addPoi("八达岭长城", "北京", "景点", "古迹/城墙", 40.3590, 116.0200, 4.7, 2,
                "07:30-17:30", 240, false, List.of("世界遗产", "徒步"),
                List.of("spring","autumn"));
        addPoi("南锣鼓巷", "北京", "景点", "街道/胡同", 39.9375, 116.4038, 4.4, 1,
                "全天", 90, false, List.of("摄影", "小吃"),
                List.of("spring","summer","autumn"));
        addPoi("全聚德烤鸭店(前门店)", "北京", "餐饮", "烤鸭", 39.8950, 116.3970, 4.5, 3,
                "11:00-14:00,17:00-21:00", 90, true, List.of("老字号", "聚餐"),
                List.of("spring","autumn","winter"));
        addPoi("簋街", "北京", "餐饮", "小吃街/夜市", 39.9340, 116.4280, 4.3, 1,
                "17:00-04:00", 60, false, List.of("夜生活", "麻辣"),
                List.of("summer","autumn"));
        addPoi("798艺术区", "北京", "娱乐", "艺术区/画廊", 39.9840, 116.4950, 4.5, 1,
                "10:00-18:00", 120, false, List.of("摄影", "文艺"),
                List.of("spring","autumn"));

        // ----- 上海 (Shanghai) -----
        addPoi("外滩", "上海", "景点", "江景/建筑群", 31.2400, 121.4900, 4.8, 1,
                "全天", 90, false, List.of("摄影", "夜景"),
                List.of("spring","summer","autumn"));
        addPoi("东方明珠", "上海", "景点", "观景台", 31.2397, 121.4998, 4.4, 3,
                "08:30-21:30", 90, true, List.of("地标", "亲子"),
                List.of("spring","summer","autumn"));
        addPoi("豫园", "上海", "景点", "园林/古典建筑", 31.2295, 121.4930, 4.5, 2,
                "08:45-16:45", 120, false, List.of("历史", "摄影"),
                List.of("spring","autumn"));
        addPoi("南京路步行街", "上海", "购物", "步行街/商场", 31.2350, 121.4770, 4.3, 2,
                "全天", 120, false, List.of("购物", "夜生活"),
                List.of("spring","summer","autumn"));
        addPoi("上海迪士尼乐园", "上海", "娱乐", "乐园", 31.1440, 121.6570, 4.7, 3,
                "08:30-20:30", 480, false, List.of("亲子", "乐园"),
                List.of("spring","summer","autumn"));
        addPoi("城隍庙小吃广场", "上海", "餐饮", "小吃/本帮菜", 31.2280, 121.4920, 4.2, 1,
                "09:00-21:00", 60, true, List.of("地道", "小吃"),
                List.of("spring","summer","autumn","winter"));
        addPoi("新天地", "上海", "餐饮", "餐厅/酒吧", 31.2190, 121.4710, 4.5, 3,
                "10:00-02:00", 120, false, List.of("夜生活", "西餐"),
                List.of("spring","summer","autumn"));

        // ----- 杭州 (Hangzhou) -----
        addPoi("西湖", "杭州", "景点", "湖/园林", 30.2390, 120.1410, 4.9, 1,
                "全天", 180, false, List.of("世界遗产", "摄影", "浪漫"),
                List.of("spring","summer","autumn"));
        addPoi("灵隐寺", "杭州", "景点", "寺庙", 30.2420, 120.0980, 4.6, 2,
                "07:00-17:30", 120, false, List.of("历史", "禅修"),
                List.of("spring","autumn"));
        addPoi("龙井村", "杭州", "景点", "茶园/村庄", 30.2250, 120.1230, 4.5, 1,
                "全天", 120, false, List.of("摄影", "茶文化"),
                List.of("spring","autumn"));
        addPoi("河坊街", "杭州", "购物", "步行街/古街", 30.2410, 120.1670, 4.3, 1,
                "全天", 90, false, List.of("小吃", "手信"),
                List.of("spring","summer","autumn"));
        addPoi("楼外楼", "杭州", "餐饮", "杭帮菜", 30.2490, 120.1330, 4.4, 3,
                "11:00-14:00,17:00-20:30", 90, true, List.of("老字号", "西湖醋鱼"),
                List.of("spring","autumn"));
        addPoi("南宋御街", "杭州", "景点", "古街/遗址", 30.2430, 120.1690, 4.2, 1,
                "全天", 60, false, List.of("历史", "购物"),
                List.of("spring","summer","autumn"));

        // ----- 成都 (Chengdu) -----
        addPoi("大熊猫繁育研究基地", "成都", "景点", "动物园/保护", 30.7340, 104.1430, 4.8, 2,
                "07:30-18:00", 180, false, List.of("亲子", "摄影"),
                List.of("spring","autumn","winter"));
        addPoi("宽窄巷子", "成都", "景点", "古街/胡同", 30.6660, 104.0590, 4.5, 1,
                "全天", 90, false, List.of("摄影", "小吃", "茶馆"),
                List.of("spring","summer","autumn","winter"));
        addPoi("锦里古街", "成都", "景点", "古街/夜市", 30.6480, 104.0480, 4.4, 1,
                "全天", 90, false, List.of("小吃", "三国文化"),
                List.of("spring","summer","autumn"));
        addPoi("都江堰", "成都", "景点", "水利/古迹", 30.9980, 103.6130, 4.7, 2,
                "08:00-17:30", 240, false, List.of("世界遗产", "工程奇观"),
                List.of("spring","autumn"));
        addPoi("蜀九香火锅(总店)", "成都", "餐饮", "火锅", 30.6550, 104.0500, 4.6, 2,
                "11:00-23:00", 90, true, List.of("麻辣", "聚餐"),
                List.of("spring","autumn","winter"));
        addPoi("人民公园茶馆", "成都", "餐饮", "茶馆", 30.6590, 104.0550, 4.3, 1,
                "07:00-21:00", 60, false, List.of("盖碗茶", "慢生活"),
                List.of("spring","summer","autumn","winter"));

        // ----- 西安 (Xi'an) -----
        addPoi("秦始皇兵马俑", "西安", "景点", "博物馆/遗址", 34.3833, 109.2733, 4.9, 3,
                "08:30-17:00", 180, true, List.of("世界遗产", "历史"),
                List.of("spring","autumn","winter"));
        addPoi("西安城墙", "西安", "景点", "城墙/古迹", 34.2596, 108.9480, 4.6, 2,
                "08:00-22:00", 120, false, List.of("骑行", "夜景"),
                List.of("spring","autumn"));
        addPoi("大雁塔", "西安", "景点", "佛塔/古迹", 34.2190, 108.9630, 4.5, 2,
                "08:00-18:00", 90, false, List.of("佛教", "夜景"),
                List.of("spring","autumn"));
        addPoi("回民街", "西安", "餐饮", "小吃街", 34.2630, 108.9430, 4.4, 1,
                "全天", 90, false, List.of("清真", "肉夹馍", "羊肉泡馍"),
                List.of("spring","summer","autumn","winter"));
        addPoi("陕西历史博物馆", "西安", "景点", "博物馆", 34.2150, 108.9530, 4.8, 1,
                "08:30-17:30", 150, true, List.of("历史", "亲子"),
                List.of("spring","autumn","winter"));
        addPoi("华清宫", "西安", "景点", "宫殿/温泉", 34.3640, 109.2110, 4.3, 3,
                "07:30-19:00", 120, false, List.of("历史", "温泉"),
                List.of("spring","autumn","winter"));

        // ----- 大理 (Dali) -----
        addPoi("洱海", "大理", "景点", "湖/自然", 25.7160, 100.1920, 4.8, 1,
                "全天", 240, false, List.of("摄影", "骑行", "浪漫"),
                List.of("spring","summer","autumn"));
        addPoi("大理古城", "大理", "景点", "古城/街道", 25.6840, 100.1680, 4.6, 1,
                "全天", 120, false, List.of("白族文化", "小吃"),
                List.of("spring","summer","autumn","winter"));
        addPoi("苍山", "大理", "景点", "山/自然", 25.6730, 100.1000, 4.5, 2,
                "08:30-17:00", 300, false, List.of("徒步", "摄影", "缆车"),
                List.of("spring","summer","autumn"));
        addPoi("喜洲古镇", "大理", "景点", "古镇/白族建筑", 25.8560, 100.1180, 4.5, 1,
                "全天", 120, false, List.of("摄影", "扎染", "粑粑"),
                List.of("spring","summer","autumn"));
        addPoi("双廊古镇", "大理", "景点", "古镇/渔村", 25.8610, 100.1930, 4.4, 2,
                "全天", 150, false, List.of("湖景", "浪漫", "民宿"),
                List.of("spring","summer","autumn"));

        // ----- 三亚 (Sanya) -----
        addPoi("亚龙湾", "三亚", "景点", "海滩", 18.2130, 109.6390, 4.7, 2,
                "全天", 240, false, List.of("潜水", "浪漫", "亲子"),
                List.of("winter","spring"));
        addPoi("天涯海角", "三亚", "景点", "海滩/岩石", 18.2940, 109.3500, 4.2, 2,
                "07:30-18:30", 120, false, List.of("摄影", "浪漫"),
                List.of("winter","spring"));
        addPoi("蜈支洲岛", "三亚", "景点", "海岛/潜水", 18.3090, 109.7630, 4.6, 3,
                "07:30-17:30", 360, false, List.of("潜水", "水上运动"),
                List.of("winter","spring","summer"));
        addPoi("三亚湾椰梦长廊", "三亚", "景点", "海滩/日落", 18.2480, 109.5070, 4.5, 1,
                "全天", 60, false, List.of("日落", "摄影", "散步"),
                List.of("spring","summer","autumn","winter"));
        addPoi("第一市场海鲜", "三亚", "餐饮", "海鲜/市场", 18.2420, 109.5150, 4.3, 2,
                "全天", 90, false, List.of("海鲜", "大排档"),
                List.of("winter","spring","summer"));
        addPoi("南山文化旅游区", "三亚", "景点", "寺庙/文化", 18.3190, 109.2260, 4.5, 3,
                "08:00-17:30", 180, false, List.of("佛教", "108米观音"),
                List.of("winter","spring"));
    }

    private void addPoi(String name, String city, String type, String subType,
                        double lat, double lng, double rating, int priceLevel,
                        String openingHours, int durationMinutes, boolean indoor,
                        List<String> tags, List<String> seasonality) {
        PoiCandidate p = new PoiCandidate(name, city, type, subType, lat, lng,
                rating, priceLevel, openingHours, durationMinutes, indoor);
        p.setTags(tags);
        p.setSeasonality(seasonality);
        allPOIs.add(p);
    }
}
