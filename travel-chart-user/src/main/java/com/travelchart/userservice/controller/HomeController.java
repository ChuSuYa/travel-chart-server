package com.travelchart.userservice.controller;

import com.travelchart.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    @GetMapping("/banners")
    public Result<Map<String, Object>> getBanners() {
        List<Map<String, Object>> banners = new ArrayList<>();

        banners.add(createBanner(1, "杭州", "/static/images/banner_hangzhou.jpg",
                "诗意江南 · 杭州", "西湖十景，人间天堂", "city_detail", "hangzhou"));
        banners.add(createBanner(2, "成都", "/static/images/banner_chengdu.jpg",
                "天府之国 · 成都", "天府广场，地标中心", "city_detail", "chengdu"));
        banners.add(createBanner(3, "西安", "/static/images/banner_xian.jpg",
                "古都长安 · 西安", "兵马俑阵，梦回大唐", "city_detail", "xian"));
        banners.add(createBanner(4, "大理", "/static/images/banner_dali.jpg",
                "风花雪月 · 大理", "苍山洱海，风花雪月", "city_detail", "dali"));
        banners.add(createBanner(5, "三亚", "/static/images/banner_sanya.jpg",
                "热带天堂 · 三亚", "碧海蓝天，椰风海韵", "city_detail", "sanya"));
        banners.add(createBanner(6, "哈尔滨", "/static/images/banner_haerbin.jpg",
                "冰雪王国 · 哈尔滨", "冰雕世界，东方莫斯科", "city_detail", "haerbin"));

        Map<String, Object> result = new HashMap<>();
        result.put("banners", banners);
        return Result.success(result);
    }

    @GetMapping("/categories")
    public Result<Map<String, Object>> getCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();
        categories.add(createCategory(1, "周末短途", ""));    // icon via emoji
        categories.add(createCategory(2, "亲子研学", ""));
        categories.add(createCategory(3, "美食之旅", ""));
        categories.add(createCategory(4, "古镇漫步", ""));
        categories.add(createCategory(5, "自然风光", ""));
        categories.add(createCategory(6, "文化古迹", ""));

        Map<String, Object> result = new HashMap<>();
        result.put("categories", categories);
        return Result.success(result);
    }

    @GetMapping("/recommends")
    public Result<Map<String, Object>> getRecommends() {
        List<Map<String, Object>> recommends = new ArrayList<>();

        recommends.add(createRecommend(1, "西湖", "杭州",
                "/static/images/rec_westlake.jpg",
                4.8, "免费", Arrays.asList("自然风光", "世界遗产")));
        recommends.add(createRecommend(2, "安顺廊桥", "成都",
                "/static/images/rec_kuanzhai.jpg",
                4.7, "免费", Arrays.asList("锦江夜景", "古桥")));
        recommends.add(createRecommend(3, "兵马俑", "西安",
                "/static/images/rec_bingmayong.jpg",
                4.9, "120元", Arrays.asList("文化古迹", "世界遗产")));
        recommends.add(createRecommend(4, "洱海", "大理",
                "/static/images/rec_erhai.jpg",
                4.7, "免费", Arrays.asList("自然风光", "骑行")));
        recommends.add(createRecommend(5, "亚龙湾", "三亚",
                "/static/images/rec_yalong.jpg",
                4.8, "免费", Arrays.asList("海滨", "水上运动")));
        recommends.add(createRecommend(6, "冰雪大世界", "哈尔滨",
                "/static/images/rec_bingxue.jpg",
                4.7, "180元", Arrays.asList("冰雪", "亲子")));

        Map<String, Object> result = new HashMap<>();
        result.put("recommends", recommends);
        return Result.success(result);
    }

    private Map<String, Object> createBanner(int id, String cityName, String imageUrl,
                                              String title, String subtitle, String linkType, String linkValue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("cityName", cityName);
        map.put("imageUrl", imageUrl);
        map.put("title", title);
        map.put("subtitle", subtitle);
        map.put("linkType", linkType);
        map.put("linkValue", linkValue);
        return map;
    }

    private Map<String, Object> createCategory(int id, String name, String icon) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("icon", icon);
        return map;
    }

    private Map<String, Object> createRecommend(int id, String name, String city,
                                                  String imageUrl, double rating, String price, List<String> tags) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("city", city);
        map.put("imageUrl", imageUrl);
        map.put("rating", rating);
        map.put("price", price);
        map.put("tags", tags);
        return map;
    }
}
