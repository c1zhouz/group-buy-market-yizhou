package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import org.apache.ibatis.annotations.Mapper;

/**
 * @description 用户拼单明细
 */
@Mapper
public interface IGroupBuyOrderListDao {

    void insert(GroupBuyOrderList groupBuyOrderListReq);

    GroupBuyOrderList queryGroupBuyOrderRecordByOutTradeNo(GroupBuyOrderList groupBuyOrderListReq);

    Integer queryOrderCountByActivityId(GroupBuyOrderList groupBuyOrderListReq);

}
