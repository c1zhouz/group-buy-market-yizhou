package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.Sku;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * @description 商品查询
 */
@Mapper
public interface ISkuDao {

    Sku querySkuByGoodsId(String goodsId);

    List<Sku> querySkuList();

    int insertSku(Sku sku);

    int updateSkuByGoodsId(Sku sku);

    int deleteSkuByGoodsId(String goodsId);

    int deleteSkuBySourceChannel(@Param("source") String source, @Param("channel") String channel);

}
