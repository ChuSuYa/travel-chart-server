package com.travelchart.userservice.service;

import com.travelchart.userservice.entity.Banner;
import com.travelchart.userservice.entity.Category;
import com.travelchart.userservice.entity.Recommend;
import com.travelchart.userservice.mapper.BannerMapper;
import com.travelchart.userservice.mapper.CategoryMapper;
import com.travelchart.userservice.mapper.RecommendMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HeatScoreService {

    private final BannerMapper bannerMapper;
    private final CategoryMapper categoryMapper;
    private final RecommendMapper recommendMapper;

    /** 衰减系数 lambda = 0.005，每天衰减 e^(-0.005 * days) */
    private static final double DECAY_FACTOR = 0.005;

    /** 权重常量 */
    private static final double W_CLICK = 1.0;
    private static final double W_VIEW = 0.3;
    private static final double W_FAVORITE = 10.0;
    private static final double W_SHARE = 8.0;
    private static final double W_COMMENT = 5.0;

    /**
     * 核心热度公式：
     * raw = base_score + click_count*1 + view_count*0.3 + favorite_count*10 + share_count*8 + comment_count*5
     * heat_score = raw * e^(-0.005 * days_since_create)
     */
    private double applyTimeDecay(double rawScore, LocalDateTime createTime) {
        long days = ChronoUnit.DAYS.between(createTime, LocalDateTime.now());
        return rawScore * Math.exp(-DECAY_FACTOR * days);
    }

    public double calculateBannerScore(Banner banner) {
        double raw = banner.getBaseScore()
                + banner.getClickCount() * W_CLICK
                + banner.getViewCount() * W_VIEW
                + banner.getFavoriteCount() * W_FAVORITE;
        return applyTimeDecay(raw, banner.getCreateTime());
    }

    public double calculateCategoryScore(Category category) {
        double raw = category.getBaseScore()
                + category.getClickCount() * W_CLICK;
        return applyTimeDecay(raw, category.getCreateTime());
    }

    public double calculateRecommendScore(Recommend recommend) {
        double raw = recommend.getBaseScore()
                + recommend.getClickCount() * W_CLICK
                + recommend.getViewCount() * W_VIEW
                + recommend.getFavoriteCount() * W_FAVORITE
                + recommend.getShareCount() * W_SHARE
                + recommend.getCommentCount() * W_COMMENT;
        return applyTimeDecay(raw, recommend.getCreateTime());
    }

    /**
     * 全量重算所有热度值并更新到数据库
     */
    public void batchRecalculate() {
        // Banners
        List<Banner> banners = bannerMapper.selectList(null);
        for (Banner b : banners) {
            b.setHeatScore(calculateBannerScore(b));
            bannerMapper.updateById(b);
        }

        // Categories
        List<Category> categories = categoryMapper.selectList(null);
        for (Category c : categories) {
            c.setHeatScore(calculateCategoryScore(c));
            categoryMapper.updateById(c);
        }

        // Recommends
        List<Recommend> recommends = recommendMapper.selectList(null);
        for (Recommend r : recommends) {
            r.setHeatScore(calculateRecommendScore(r));
            recommendMapper.updateById(r);
        }
    }
}
