package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.GroupBuyOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description 用户拼单
 */
@Mapper
public interface IGroupBuyOrderDao {

    void insert(GroupBuyOrder groupBuyOrder);

    int updateAddLockCount(String teamId);

    int updateSubtractionLockCount(String teamId);

    int updateOrderStatus2CLOSEIfEmpty(String teamId);

    GroupBuyOrder queryGroupBuyProgress(String teamId);

    GroupBuyOrder queryGroupBuyTeamByTeamId(String teamId);

    int updateAddCompleteCount(String teamId);

    int updateOrderStatus2COMPLETE(String teamId);

    int closeInProgressTeamByActivityIdGoodsId(@Param("activityId") Long activityId, @Param("goodsId") String goodsId);

    List<GroupBuyOrder> queryGroupBuyProgressByTeamIds(@Param("teamIds") Set<String> teamIds);

    Integer queryAllTeamCount(@Param("teamIds") Set<String> teamIds);

    Integer queryAllTeamCompleteCount(@Param("teamIds") Set<String> teamIds);

    Integer queryAllUserCount(@Param("teamIds") Set<String> teamIds);

}
