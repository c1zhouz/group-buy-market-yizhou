package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.GroupBuyActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @description 拼团活动Dao
 */
@Mapper
public interface IGroupBuyActivityDao {

    List<GroupBuyActivity> queryGroupBuyActivityList();

    GroupBuyActivity queryValidGroupBuyActivity(GroupBuyActivity groupBuyActivityReq);

    GroupBuyActivity queryValidGroupBuyActivityId(Long activityId);

    GroupBuyActivity queryGroupBuyActivityByActivityId(Long activityId);

    int insertGroupBuyActivity(GroupBuyActivity groupBuyActivity);

    Integer queryGroupBuyActivityStatusByActivityId(@Param("activityId") Long activityId);

    int updateActivityStatusByActivityId(@Param("activityId") Long activityId, @Param("fromStatus") Integer fromStatus, @Param("toStatus") Integer toStatus);

    int updateActivityNameByActivityId(@Param("activityId") Long activityId, @Param("activityName") String activityName);

    int updateGroupBuyActivityByActivityId(GroupBuyActivity groupBuyActivity);

    List<Long> queryActivityIdsByDiscountId(@Param("discountId") String discountId);

    int deleteGroupBuyActivityByActivityId(@Param("activityId") Long activityId);

}
