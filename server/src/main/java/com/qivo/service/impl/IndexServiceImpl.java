package com.qivo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qivo.cache.HomepageCache;
import com.qivo.mapper.BannerMapper;
import com.qivo.mapper.ProductCategoryMapper;
import com.qivo.mapper.ProductMapper;
import com.qivo.service.IndexService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IndexServiceImpl implements IndexService {

    private final BannerMapper bannerMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final HomepageCache homepageCache;

    public IndexServiceImpl(
            BannerMapper bannerMapper,
            ProductMapper productMapper,
            ProductCategoryMapper categoryMapper,
            HomepageCache homepageCache) {
        this.bannerMapper = bannerMapper;
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.homepageCache = homepageCache;
    }

    @Override
    public Map<String, Object> indexData() {
        // 先读缓存
        Map<String, Object> cached = homepageCache.read();
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，查库
        Map<String, Object> data = new HashMap<>();
        data.put("banners", bannerMapper.selectAllEnabled());
        data.put(
                "hotProducts",
                productMapper.selectPage(new Page<>(1, 8, false), null, null, 1, "sales"));
        data.put(
                "newProducts",
                productMapper.selectPage(new Page<>(1, 8, false), null, null, 1, null));
        data.put("categories", categoryMapper.selectAll());

        // 写入缓存
        homepageCache.write(data);
        return data;
    }
}
