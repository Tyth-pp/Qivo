package com.qivo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qivo.cache.HomepageCache;
import com.qivo.entity.Product;
import com.qivo.entity.ProductCategory;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.ProductCategoryMapper;
import com.qivo.mapper.ProductCollectMapper;
import com.qivo.mapper.ProductMapper;
import com.qivo.persistence.PageRequests;
import com.qivo.result.ErrorCode;
import com.qivo.service.ProductService;
import com.qivo.service.SkuCatalogService;
import com.qivo.vo.PageVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class ProductServiceImpl implements ProductService {

    private final SkuCatalogService catalog;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductCollectMapper collectMapper;
    private final HomepageCache homepageCache;

    public ProductServiceImpl(
            ProductMapper productMapper,
            ProductCategoryMapper categoryMapper,
            ProductCollectMapper collectMapper,
            HomepageCache homepageCache,
            SkuCatalogService catalog) {
        this.catalog = catalog;
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.collectMapper = collectMapper;
        this.homepageCache = homepageCache;
    }

    @Override
    public PageVO<Product> page(
            int page, int pageSize, String keyword, Long categoryId, Integer status, String sort) {
        if (sort != null && !Set.of("", "price_asc", "price_desc", "sales").contains(sort))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        Page<Product> pagination = PageRequests.of(page, pageSize);
        List<Product> list =
                productMapper.selectPage(pagination, keyword, categoryId, status, sort);
        return PageVO.of(list, pagination.getTotal(), page, pageSize);
    }

    @Override
    public Product getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_EXIST);
        catalog.hydrate(product);
        return product;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        Product locked = productMapper.lockById(id);
        if (locked == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_EXIST);
        catalog.hydrate(locked);
        if (status == 1 && locked.getPrice() == null)
            throw new BusinessException(400, "无启用规格，无法上架");
        productMapper.updateStatus(id, status);
        homepageCache.evict();
    }

    @Override
    public List<ProductCategory> categoryList() {
        return categoryMapper.selectAll();
    }

    @Override
    public List<ProductCategory> categoryTree() {
        return categoryMapper.selectAll();
    }

    @Override
    @Transactional
    public void addCategory(ProductCategory category) {
        categoryMapper.insert(category);
        homepageCache.evict();
    }

    @Override
    @Transactional
    public void updateCategory(ProductCategory category) {
        categoryMapper.update(category);
        homepageCache.evict();
    }

    // 收藏
    @Override
    @Transactional
    public void collect(Long userId, Long productId) {
        if (productMapper.selectById(productId) == null)
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EXIST);
        if (collectMapper.exists(userId, productId) == 0) {
            collectMapper.insert(userId, productId);
        }
    }

    @Override
    @Transactional
    public void cancelCollect(Long userId, Long productId) {
        collectMapper.delete(userId, productId);
    }

    @Override
    public List<Long> collectList(Long userId) {
        return collectMapper.selectByUserId(userId).stream().map(c -> c.getProductId()).toList();
    }

    @Override
    public boolean isCollected(Long userId, Long productId) {
        return collectMapper.exists(userId, productId) > 0;
    }
}
