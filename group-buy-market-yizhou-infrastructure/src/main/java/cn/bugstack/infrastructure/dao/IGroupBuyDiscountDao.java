package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.GroupBuyDiscount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @description 折扣配置Dao
 */
@Mapper
public interface IGroupBuyDiscountDao {

    List<GroupBuyDiscount> queryGroupBuyDiscountList();

    GroupBuyDiscount queryGroupBuyActivityDiscountByDiscountId(String discountId);

    List<String> queryDiscountIdsByMarketPlanExpr(@Param("marketPlan") String marketPlan, @Param("marketExpr") String marketExpr);

    int insertGroupBuyDiscount(GroupBuyDiscount groupBuyDiscount);

    int deleteGroupBuyDiscountByDiscountId(@Param("discountId") String discountId);

}
