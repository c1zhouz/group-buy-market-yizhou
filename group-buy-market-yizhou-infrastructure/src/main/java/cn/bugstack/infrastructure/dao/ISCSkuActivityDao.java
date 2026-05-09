package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.SCSkuActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @description 渠道商品活动配置关联表Dao
 */
@Mapper
public interface ISCSkuActivityDao {

    SCSkuActivity querySCSkuActivityBySCGoodsId(SCSkuActivity scSkuActivity);

    int insertSCSkuActivity(SCSkuActivity scSkuActivity);

    int deleteSCSkuActivityByGoodsId(@Param("source") String source, @Param("channel") String channel, @Param("goodsId") String goodsId);

    int deleteSCSkuActivityByActivityId(@Param("activityId") Long activityId);

    int deleteSCSkuActivityBySourceChannel(@Param("source") String source, @Param("channel") String channel);

}
