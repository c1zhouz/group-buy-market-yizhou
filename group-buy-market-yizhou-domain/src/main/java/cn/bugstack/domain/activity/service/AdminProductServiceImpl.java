package cn.bugstack.domain.activity.service;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.entity.AdminProductListEntity;
import cn.bugstack.domain.activity.model.entity.DefaultProductInitializeEntity;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * @description 后台商品管理领域服务
 */
@Service
public class AdminProductServiceImpl implements IAdminProductService {

    private static final String DEFAULT_SOURCE = "s01";
    private static final String DEFAULT_CHANNEL = "c01";

    @Resource
    private IActivityRepository repository;

    @Override
    public AdminProductListEntity queryProductList() {
        return repository.queryAdminProductList(DEFAULT_SOURCE, DEFAULT_CHANNEL);
    }

    @Override
    public boolean createProduct(String goodsId, String goodsName, BigDecimal price, Integer stock, Long activityId) {
        return repository.createProduct(DEFAULT_SOURCE, DEFAULT_CHANNEL, goodsId, goodsName, price, stock, activityId);
    }

    @Override
    public boolean updateProduct(String goodsId, String goodsName, BigDecimal price, Integer stock, Long activityId) {
        return repository.updateProduct(DEFAULT_SOURCE, DEFAULT_CHANNEL, goodsId, goodsName, price, stock, activityId);
    }

    @Override
    public boolean deleteProduct(String goodsId) {
        return repository.deleteProduct(DEFAULT_SOURCE, DEFAULT_CHANNEL, goodsId);
    }

    @Override
    public DefaultProductInitializeEntity initializeDefaultProducts() {
        return repository.initializeDefaultProducts(DEFAULT_SOURCE, DEFAULT_CHANNEL);
    }

}
