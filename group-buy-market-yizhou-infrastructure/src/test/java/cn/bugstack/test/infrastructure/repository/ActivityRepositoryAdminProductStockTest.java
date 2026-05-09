package cn.bugstack.test.infrastructure.repository;

import cn.bugstack.domain.activity.model.entity.AdminProductEntity;
import cn.bugstack.domain.activity.model.entity.AdminProductListEntity;
import cn.bugstack.infrastructure.adapter.repository.ActivityRepository;
import cn.bugstack.infrastructure.adapter.repository.SkuDefaultService;
import cn.bugstack.infrastructure.dao.IGroupBuyActivityDao;
import cn.bugstack.infrastructure.dao.IGroupBuyDiscountDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.ISCSkuActivityDao;
import cn.bugstack.infrastructure.dao.ISkuDao;
import cn.bugstack.infrastructure.dao.po.Sku;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ActivityRepositoryAdminProductStockTest {

    @Test
    public void queryAdminProductList_shouldSubtractPaidOrdersFromDefaultStock() throws Exception {
        ActivityRepository repository = new ActivityRepository();
        ISkuDao skuDao = Mockito.mock(ISkuDao.class);
        ISCSkuActivityDao skuActivityDao = Mockito.mock(ISCSkuActivityDao.class);
        IGroupBuyDiscountDao groupBuyDiscountDao = Mockito.mock(IGroupBuyDiscountDao.class);
        IGroupBuyActivityDao groupBuyActivityDao = Mockito.mock(IGroupBuyActivityDao.class);
        IGroupBuyOrderListDao groupBuyOrderListDao = Mockito.mock(IGroupBuyOrderListDao.class);

        setField(repository, "skuDao", skuDao);
        setField(repository, "skuActivityDao", skuActivityDao);
        setField(repository, "groupBuyDiscountDao", groupBuyDiscountDao);
        setField(repository, "groupBuyActivityDao", groupBuyActivityDao);
        setField(repository, "groupBuyOrderListDao", groupBuyOrderListDao);
        setField(repository, "skuDefaultService", Mockito.mock(SkuDefaultService.class));

        String goodsId = "1104005";
        Sku fold = Sku.builder()
                .source("s01")
                .channel("c01")
                .goodsId(goodsId)
                .goodsName("HUAWEI MateBook Fold")
                .originalPrice(new BigDecimal("23999.00"))
                .build();
        Map<String, Object> paidCountRow = new HashMap<>();
        paidCountRow.put("goodsId", goodsId);
        paidCountRow.put("orderCount", 1L);

        Mockito.when(skuDao.querySkuList()).thenReturn(Collections.singletonList(fold));
        Mockito.when(groupBuyDiscountDao.queryGroupBuyDiscountList()).thenReturn(Collections.emptyList());
        Mockito.when(groupBuyOrderListDao.queryAdminOrderCountByStatusGroupByGoodsId(1)).thenReturn(Collections.singletonList(paidCountRow));

        AdminProductListEntity result = repository.queryAdminProductList("s01", "c01");

        AdminProductEntity product = result.getProductList().get(0);
        Assert.assertEquals(goodsId, product.getGoodsId());
        Assert.assertEquals(Integer.valueOf(99), product.getStock());
    }

    @Test
    public void queryAdminProductList_shouldUseConfiguredStockBeforeSubtractingPaidOrders() throws Exception {
        ActivityRepository repository = new ActivityRepository();
        ISkuDao skuDao = Mockito.mock(ISkuDao.class);
        ISCSkuActivityDao skuActivityDao = Mockito.mock(ISCSkuActivityDao.class);
        IGroupBuyDiscountDao groupBuyDiscountDao = Mockito.mock(IGroupBuyDiscountDao.class);
        IGroupBuyActivityDao groupBuyActivityDao = Mockito.mock(IGroupBuyActivityDao.class);
        IGroupBuyOrderListDao groupBuyOrderListDao = Mockito.mock(IGroupBuyOrderListDao.class);

        setField(repository, "skuDao", skuDao);
        setField(repository, "skuActivityDao", skuActivityDao);
        setField(repository, "groupBuyDiscountDao", groupBuyDiscountDao);
        setField(repository, "groupBuyActivityDao", groupBuyActivityDao);
        setField(repository, "groupBuyOrderListDao", groupBuyOrderListDao);
        setField(repository, "skuDefaultService", Mockito.mock(SkuDefaultService.class));

        String goodsId = "1104005";
        Sku fold = Sku.builder()
                .source("s01")
                .channel("c01")
                .goodsId(goodsId)
                .goodsName("HUAWEI MateBook Fold")
                .originalPrice(new BigDecimal("23999.00"))
                .stock(120)
                .build();
        Map<String, Object> paidCountRow = new HashMap<>();
        paidCountRow.put("goodsId", goodsId);
        paidCountRow.put("orderCount", 1L);

        Mockito.when(skuDao.querySkuList()).thenReturn(Collections.singletonList(fold));
        Mockito.when(groupBuyDiscountDao.queryGroupBuyDiscountList()).thenReturn(Collections.emptyList());
        Mockito.when(groupBuyOrderListDao.queryAdminOrderCountByStatusGroupByGoodsId(1)).thenReturn(Collections.singletonList(paidCountRow));

        AdminProductListEntity result = repository.queryAdminProductList("s01", "c01");

        AdminProductEntity product = result.getProductList().get(0);
        Assert.assertEquals(Integer.valueOf(119), product.getStock());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
