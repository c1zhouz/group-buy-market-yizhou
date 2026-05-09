package cn.bugstack.domain.activity.adapter.repository;

import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.entity.AdminProductListEntity;
import cn.bugstack.domain.activity.model.entity.DefaultProductInitializeEntity;
import cn.bugstack.domain.activity.model.entity.MarketProductItemEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.SCSkuActivityVO;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动仓储
 * @create 2024-12-21 10:06
 */
public interface IActivityRepository {

    GroupBuyActivityDiscountVO queryGroupBuyActivityDiscountVO(Long activityId);

    SkuVO querySkuByGoodsId(String goodsId);

    SCSkuActivityVO querySCSkuActivityBySCGoodsId(String source, String channel, String goodsId);

    List<MarketProductItemEntity> queryMarketProductList(String source, String channel);

    AdminProductListEntity queryAdminProductList(String source, String channel);

    boolean createProduct(String source, String channel, String goodsId, String goodsName, BigDecimal price, Long activityId);

    boolean updateProduct(String source, String channel, String goodsId, String goodsName, BigDecimal price, Long activityId);

    boolean deleteProduct(String source, String channel, String goodsId);

    DefaultProductInitializeEntity initializeDefaultProducts(String source, String channel);

    boolean isTagCrowdRange(String tagId, String userId);

    boolean downgradeSwitch();

    boolean cutRange(String userId);

    List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByOwner(Long activityId, String goodsId, String userId, Integer ownerCount);

    List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByRandom(Long activityId, String goodsId, String userId, Integer randomCount);

    String queryTeamLeaderUserIdByTeamId(String teamId);

    List<String> queryTeamMemberUserIdsByTeamId(String teamId);

    TeamStatisticVO queryTeamStatisticByActivityId(Long activityId);

}
