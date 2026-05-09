package cn.bugstack.domain.activity.service;

import cn.bugstack.domain.activity.model.entity.AdminProductListEntity;
import cn.bugstack.domain.activity.model.entity.DefaultProductInitializeEntity;

import java.math.BigDecimal;

/**
 * @description 后台商品管理服务
 */
public interface IAdminProductService {

    AdminProductListEntity queryProductList();

    boolean createProduct(String goodsId, String goodsName, BigDecimal price, Integer stock, Long activityId);

    boolean updateProduct(String goodsId, String goodsName, BigDecimal price, Integer stock, Long activityId);

    boolean deleteProduct(String goodsId);

    DefaultProductInitializeEntity initializeDefaultProducts();

}
