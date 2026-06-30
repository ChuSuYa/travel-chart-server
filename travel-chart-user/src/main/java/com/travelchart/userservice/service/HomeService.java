package com.travelchart.userservice.service;

import com.travelchart.userservice.entity.Banner;
import com.travelchart.userservice.entity.Category;
import com.travelchart.userservice.entity.Recommend;
import com.travelchart.userservice.mapper.BannerMapper;
import com.travelchart.userservice.mapper.CategoryMapper;
import com.travelchart.userservice.mapper.RecommendMapper;
import com.travelchart.userservice.vo.BannerVO;
import com.travelchart.userservice.vo.CategoryVO;
import com.travelchart.userservice.vo.RecommendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final BannerMapper bannerMapper;
    private final CategoryMapper categoryMapper;
    private final RecommendMapper recommendMapper;

    public List<BannerVO> getBanners() {
        return bannerMapper.selectEnabledBanners().stream()
                .map(this::toBannerVO)
                .collect(Collectors.toList());
    }

    public List<CategoryVO> getCategories() {
        return categoryMapper.selectEnabledCategories().stream()
                .map(this::toCategoryVO)
                .collect(Collectors.toList());
    }

    public List<RecommendVO> getRecommends() {
        return recommendMapper.selectEnabledRecommends().stream()
                .map(this::toRecommendVO)
                .collect(Collectors.toList());
    }

    private BannerVO toBannerVO(Banner b) {
        BannerVO vo = new BannerVO();
        vo.setId(b.getId());
        vo.setCityName(b.getCityName());
        vo.setImageUrl(b.getImageUrl());
        vo.setTitle(b.getTitle());
        vo.setSubtitle(b.getSubtitle());
        vo.setLinkType(b.getLinkType());
        vo.setLinkValue(b.getLinkValue());
        return vo;
    }

    private CategoryVO toCategoryVO(Category c) {
        CategoryVO vo = new CategoryVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setIcon(c.getIcon());
        return vo;
    }

    private RecommendVO toRecommendVO(Recommend r) {
        RecommendVO vo = new RecommendVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        vo.setCity(r.getCity());
        vo.setImageUrl(r.getImageUrl());
        vo.setRating(r.getRating());
        vo.setPrice(r.getPrice() != null ? r.getPrice().toString() : null);
        vo.setTags(r.getTags());
        return vo;
    }
}
