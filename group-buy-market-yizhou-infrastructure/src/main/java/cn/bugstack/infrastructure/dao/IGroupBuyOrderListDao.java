package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @description 用户拼单明细
 */
@Mapper
public interface IGroupBuyOrderListDao {

    void insert(GroupBuyOrderList groupBuyOrderListReq);

    GroupBuyOrderList queryGroupBuyOrderRecordByOutTradeNo(GroupBuyOrderList groupBuyOrderListReq);

    GroupBuyOrderList queryGroupBuyOrderByOutTradeNo(@Param("userId") String userId, @Param("outTradeNo") String outTradeNo);

    GroupBuyOrderList queryNoPayOrderByUserActivityGoods(GroupBuyOrderList groupBuyOrderListReq);

    Integer queryOrderCountByActivityId(GroupBuyOrderList groupBuyOrderListReq);

    Integer queryOrderCountByGoodsId(GroupBuyOrderList groupBuyOrderListReq);

    int updateOrderStatus2COMPLETE(GroupBuyOrderList groupBuyOrderListReq);

    int updateOrderStatus2CLOSE(@Param("userId") String userId, @Param("outTradeNo") String outTradeNo);

    List<String> queryGroupBuyCompleteOrderOutTradeNoListByTeamId(String teamId);

    String queryTeamLeaderUserIdByTeamId(@Param("teamId") String teamId);

    List<String> queryTeamMemberUserIdsByTeamId(@Param("teamId") String teamId);

    List<GroupBuyOrderList> queryInProgressUserGroupBuyOrderDetailListByUserId(GroupBuyOrderList groupBuyOrderListReq);

    List<GroupBuyOrderList> queryInProgressUserGroupBuyOrderDetailListByRandom(GroupBuyOrderList groupBuyOrderListReq);

    List<GroupBuyOrderList> queryInProgressUserGroupBuyOrderDetailListByActivityId(Long activityId);

    List<GroupBuyOrderList> queryAdminOrderList();

    List<GroupBuyOrderList> queryAdminOrderListByStatus(@Param("status") Integer status);

    Integer queryAdminOrderCountAll();

    Integer queryAdminOrderCountByStatus(@Param("status") Integer status);

    BigDecimal queryAdminGMVByStatus(@Param("status") Integer status);

    List<Map<String, Object>> queryAdminChannelStats();

    List<Map<String, Object>> queryAdminUserList(@Param("keyword") String keyword,
                                                 @Param("offset") Integer offset,
                                                 @Param("pageSize") Integer pageSize);

    Integer queryAdminUserCount(@Param("keyword") String keyword);

    List<GroupBuyOrderList> queryAdminOrderListByUserId(@Param("userId") String userId,
                                                         @Param("offset") Integer offset,
                                                         @Param("pageSize") Integer pageSize);

    Integer queryAdminOrderCountByUserId(@Param("userId") String userId);

    int deleteAdminOrderByUserIdOutTradeNo(@Param("userId") String userId, @Param("outTradeNo") String outTradeNo);

    int clearInProgressAbnormalByUserIdGoodsId(@Param("userId") String userId, @Param("goodsId") String goodsId);

    int closeInProgressOrderListByActivityIdGoodsId(@Param("activityId") Long activityId, @Param("goodsId") String goodsId);

}
