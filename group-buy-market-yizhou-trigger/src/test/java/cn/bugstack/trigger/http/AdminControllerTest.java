package cn.bugstack.trigger.http;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.activity.model.entity.AdminProductEntity;
import cn.bugstack.domain.activity.model.entity.AdminProductListEntity;
import cn.bugstack.domain.activity.model.entity.DefaultProductInitializeEntity;
import cn.bugstack.domain.activity.service.IAdminProductService;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.infrastructure.dao.IGroupBuyActivityDao;
import cn.bugstack.infrastructure.dao.IGroupBuyDiscountDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyActivity;
import cn.bugstack.infrastructure.dao.po.GroupBuyDiscount;
import cn.bugstack.types.enums.ResponseCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AdminControllerTest {

    @Test
    public void queryProducts_shouldRejectForgedAdminHeaderWithoutLoginCookie() {
        AdminController controller = new AdminController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Admin-User", "mallory");

        Response<?> response = controller.queryProducts(request);

        Assertions.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assertions.assertEquals("未登录或登录已过期", response.getInfo());
    }

    @Test
    public void deleteOrder_shouldCancelThroughTradeDomainInsteadOfPhysicalDelete() {
        AdminController controller = new AdminController();
        IGroupBuyOrderListDao groupBuyOrderListDao = Mockito.mock(IGroupBuyOrderListDao.class);
        ITradeLockOrderService tradeLockOrderService = Mockito.mock(ITradeLockOrderService.class);
        ReflectionTestUtils.setField(controller, "groupBuyOrderListDao", groupBuyOrderListDao);
        ReflectionTestUtils.setField(controller, "tradeOrderService", tradeLockOrderService);
        Mockito.when(tradeLockOrderService.queryNoPayMarketPayOrderByOutTradeNo("u001", "otn001"))
                .thenReturn(MarketPayOrderEntity.builder().orderId("order001").build());
        Mockito.when(tradeLockOrderService.cancelNoPayMarketPayOrder("u001", "otn001")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));

        Response<Boolean> response = controller.deleteOrder(request, "otn001", "u001");

        Assertions.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assertions.assertEquals(Boolean.TRUE, response.getData());
        Mockito.verify(tradeLockOrderService).cancelNoPayMarketPayOrder("u001", "otn001");
        Mockito.verify(groupBuyOrderListDao, Mockito.never()).deleteAdminOrderByUserIdOutTradeNo(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void deleteOrder_shouldExplainOnlyPendingOrdersCanBeCanceled() {
        AdminController controller = new AdminController();
        ITradeLockOrderService tradeLockOrderService = Mockito.mock(ITradeLockOrderService.class);
        ReflectionTestUtils.setField(controller, "tradeOrderService", tradeLockOrderService);
        Mockito.when(tradeLockOrderService.queryNoPayMarketPayOrderByOutTradeNo("u001", "otn001")).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));

        Response<Boolean> response = controller.deleteOrder(request, "otn001", "u001");

        Assertions.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assertions.assertEquals("只能取消待支付订单，请先筛选待支付订单后操作", response.getInfo());
        Assertions.assertEquals(Boolean.FALSE, response.getData());
        Mockito.verify(tradeLockOrderService, Mockito.never()).cancelNoPayMarketPayOrder(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void queryMarketingActivities_shouldNormalizeDisplayNameWithoutUpdatingDatabase() {
        AdminController controller = new AdminController();
        IGroupBuyActivityDao groupBuyActivityDao = Mockito.mock(IGroupBuyActivityDao.class);
        IGroupBuyDiscountDao groupBuyDiscountDao = Mockito.mock(IGroupBuyDiscountDao.class);
        ReflectionTestUtils.setField(controller, "groupBuyActivityDao", groupBuyActivityDao);
        ReflectionTestUtils.setField(controller, "groupBuyDiscountDao", groupBuyDiscountDao);

        GroupBuyActivity activity = new GroupBuyActivity();
        activity.setActivityId(1001L);
        activity.setActivityName("手写MyBatis旧活动");
        activity.setDiscountId("25120208");
        activity.setTarget(3);
        activity.setStatus(1);
        activity.setGroupType(0);
        Mockito.when(groupBuyActivityDao.queryGroupBuyActivityList()).thenReturn(Collections.singletonList(activity));

        GroupBuyDiscount discount = new GroupBuyDiscount();
        discount.setDiscountId(25120208);
        discount.setDiscountName("测试优惠");
        discount.setMarketPlan("N");
        discount.setMarketExpr("599");
        Mockito.when(groupBuyDiscountDao.queryGroupBuyDiscountList()).thenReturn(Collections.singletonList(discount));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));

        Response<Map<String, Object>> response = controller.queryMarketingActivities(request);

        Assertions.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        List<?> list = (List<?>) response.getData().get("list");
        Map<?, ?> firstActivity = (Map<?, ?>) list.get(0);
        Assertions.assertEquals("3人拼团活动", firstActivity.get("name"));
        Assertions.assertEquals("手写MyBatis旧活动", activity.getActivityName());
        Mockito.verify(groupBuyActivityDao, Mockito.never()).updateActivityNameByActivityId(Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    public void createProduct_shouldRejectMissingSkuWithoutWritingDatabase() {
        AdminController controller = new AdminController();
        IAdminProductService adminProductService = Mockito.mock(IAdminProductService.class);
        ReflectionTestUtils.setField(controller, "adminProductService", adminProductService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));
        Map<String, Object> requestDTO = new HashMap<>();
        requestDTO.put("name", "测试商品");
        requestDTO.put("price", "99.00");

        Response<Boolean> response = controller.createProduct(request, requestDTO);

        Assertions.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assertions.assertEquals("商品名称、SKU、售价不能为空", response.getInfo());
        Assertions.assertEquals(Boolean.FALSE, response.getData());
        Mockito.verify(adminProductService, Mockito.never()).createProduct(Mockito.anyString(), Mockito.anyString(), Mockito.any(BigDecimal.class), Mockito.any(), Mockito.any());
    }

    @Test
    public void updateProduct_shouldRejectInvalidPriceAsIllegalParameterWithoutWritingDatabase() {
        AdminController controller = new AdminController();
        IAdminProductService adminProductService = Mockito.mock(IAdminProductService.class);
        ReflectionTestUtils.setField(controller, "adminProductService", adminProductService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));
        Map<String, Object> requestDTO = new HashMap<>();
        requestDTO.put("name", "测试商品");
        requestDTO.put("price", "not-a-number");

        Response<Boolean> response = controller.updateProduct(request, "10001", requestDTO);

        Assertions.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assertions.assertEquals("售价格式不正确", response.getInfo());
        Assertions.assertEquals(Boolean.FALSE, response.getData());
        Mockito.verify(adminProductService, Mockito.never()).updateProduct(Mockito.anyString(), Mockito.anyString(), Mockito.any(BigDecimal.class), Mockito.any(), Mockito.any());
    }

    @Test
    public void updateProduct_shouldRejectNegativeStockWithoutWritingDatabase() {
        AdminController controller = new AdminController();
        IAdminProductService adminProductService = Mockito.mock(IAdminProductService.class);
        ReflectionTestUtils.setField(controller, "adminProductService", adminProductService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));
        Map<String, Object> requestDTO = new HashMap<>();
        requestDTO.put("name", "测试商品");
        requestDTO.put("price", "99.00");
        requestDTO.put("stock", "-1");

        Response<Boolean> response = controller.updateProduct(request, "10001", requestDTO);

        Assertions.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assertions.assertEquals("库存不能为负数", response.getInfo());
        Assertions.assertEquals(Boolean.FALSE, response.getData());
        Mockito.verify(adminProductService, Mockito.never()).updateProduct(Mockito.anyString(), Mockito.anyString(), Mockito.any(BigDecimal.class), Mockito.any(), Mockito.any());
    }

    @Test
    public void createProduct_shouldDelegateValidatedProductCommandToDomainService() {
        AdminController controller = new AdminController();
        IAdminProductService adminProductService = Mockito.mock(IAdminProductService.class);
        ReflectionTestUtils.setField(controller, "adminProductService", adminProductService);
        Mockito.when(adminProductService.createProduct("10001", "测试商品", new BigDecimal("99.00"), 25, 1001L)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));
        Map<String, Object> requestDTO = new HashMap<>();
        requestDTO.put("sku", "10001");
        requestDTO.put("name", "测试商品");
        requestDTO.put("price", "99.00");
        requestDTO.put("stock", "25");
        requestDTO.put("activityId", "1001");

        Response<Boolean> response = controller.createProduct(request, requestDTO);

        Assertions.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assertions.assertEquals(Boolean.TRUE, response.getData());
        Mockito.verify(adminProductService).createProduct("10001", "测试商品", new BigDecimal("99.00"), 25, 1001L);
    }

    @Test
    public void queryProducts_shouldDelegateProductListToDomainService() {
        AdminController controller = new AdminController();
        IAdminProductService adminProductService = Mockito.mock(IAdminProductService.class);
        ReflectionTestUtils.setField(controller, "adminProductService", adminProductService);
        AdminProductEntity product = AdminProductEntity.builder()
                .goodsId("10001")
                .goodsName("测试商品")
                .price(new BigDecimal("99.00"))
                .activityId(1001L)
                .activityName("3人拼团活动")
                .activityType("3人拼团 / N599")
                .activityTypeDisplay("3人拼团 / N599")
                .build();
        Mockito.when(adminProductService.queryProductList()).thenReturn(AdminProductListEntity.builder()
                .productList(Collections.singletonList(product))
                .activityTypes(Collections.singletonList("N599"))
                .build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));

        Response<Map<String, Object>> response = controller.queryProducts(request);

        Assertions.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Map<String, Object> data = response.getData();
        Map<?, ?> summary = (Map<?, ?>) data.get("summary");
        List<?> list = (List<?>) data.get("list");
        Map<?, ?> firstProduct = (Map<?, ?>) list.get(0);
        Assertions.assertEquals(1, summary.get("total"));
        Assertions.assertEquals("测试商品", firstProduct.get("name"));
        Assertions.assertEquals("10001", firstProduct.get("sku"));
        Assertions.assertEquals(1001L, firstProduct.get("activityId"));
        Assertions.assertEquals(Collections.singletonList("N599"), data.get("activityTypes"));
        Mockito.verify(adminProductService).queryProductList();
    }

    @Test
    public void initializeDefaultProducts_shouldDelegateToDomainService() {
        AdminController controller = new AdminController();
        IAdminProductService adminProductService = Mockito.mock(IAdminProductService.class);
        ReflectionTestUtils.setField(controller, "adminProductService", adminProductService);
        Mockito.when(adminProductService.initializeDefaultProducts()).thenReturn(DefaultProductInitializeEntity.builder()
                .total(2)
                .inserted(2)
                .defaultActivityId(1001L)
                .build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));

        Response<Map<String, Object>> response = controller.initializeDefaultProducts(request);

        Assertions.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assertions.assertEquals(2, response.getData().get("total"));
        Assertions.assertEquals(2, response.getData().get("inserted"));
        Assertions.assertEquals(1001L, response.getData().get("defaultActivityId"));
        Mockito.verify(adminProductService).initializeDefaultProducts();
    }

    @Test
    public void createMarketingActivity_shouldNotPurgeDiscountTiersAsHiddenSideEffect() {
        AdminController controller = new AdminController();
        IGroupBuyActivityDao groupBuyActivityDao = Mockito.mock(IGroupBuyActivityDao.class);
        IGroupBuyDiscountDao groupBuyDiscountDao = Mockito.mock(IGroupBuyDiscountDao.class);
        ReflectionTestUtils.setField(controller, "groupBuyActivityDao", groupBuyActivityDao);
        ReflectionTestUtils.setField(controller, "groupBuyDiscountDao", groupBuyDiscountDao);

        GroupBuyDiscount discount = new GroupBuyDiscount();
        discount.setDiscountId(25120208);
        discount.setMarketPlan("N");
        discount.setMarketExpr("599");
        Mockito.when(groupBuyDiscountDao.queryGroupBuyActivityDiscountByDiscountId("25120208")).thenReturn(discount);
        Mockito.when(groupBuyActivityDao.queryGroupBuyActivityList()).thenReturn(Collections.emptyList());
        Mockito.when(groupBuyActivityDao.insertGroupBuyActivity(Mockito.any(GroupBuyActivity.class))).thenReturn(1);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));
        Map<String, Object> requestDTO = new HashMap<>();
        requestDTO.put("name", "3人拼团活动");
        requestDTO.put("startTimestamp", "1893456000000");
        requestDTO.put("endTimestamp", "1924992000000");
        requestDTO.put("target", "3");
        requestDTO.put("discountId", "25120208");

        Response<Boolean> response = controller.createMarketingActivity(request, requestDTO);

        Assertions.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assertions.assertEquals(Boolean.TRUE, response.getData());
        Mockito.verify(groupBuyDiscountDao, Mockito.never()).queryDiscountIdsByMarketPlanExpr(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(groupBuyDiscountDao, Mockito.never()).deleteGroupBuyDiscountByDiscountId(Mockito.anyString());
    }

    @Test
    public void createMarketingActivity_shouldInsertNewActivityWhenSameTypeAlreadyExists() {
        AdminController controller = new AdminController();
        IGroupBuyActivityDao groupBuyActivityDao = Mockito.mock(IGroupBuyActivityDao.class);
        IGroupBuyDiscountDao groupBuyDiscountDao = Mockito.mock(IGroupBuyDiscountDao.class);
        ReflectionTestUtils.setField(controller, "groupBuyActivityDao", groupBuyActivityDao);
        ReflectionTestUtils.setField(controller, "groupBuyDiscountDao", groupBuyDiscountDao);

        GroupBuyDiscount discount = new GroupBuyDiscount();
        discount.setDiscountId(25120208);
        discount.setMarketPlan("N");
        discount.setMarketExpr("599");
        Mockito.when(groupBuyDiscountDao.queryGroupBuyActivityDiscountByDiscountId("25120208")).thenReturn(discount);

        GroupBuyActivity oldActivity = new GroupBuyActivity();
        oldActivity.setActivityId(1001L);
        oldActivity.setTarget(3);
        oldActivity.setDiscountId("25120208");
        oldActivity.setStatus(0);
        GroupBuyActivity latestActivity = new GroupBuyActivity();
        latestActivity.setActivityId(1002L);
        latestActivity.setTarget(3);
        latestActivity.setDiscountId("25120208");
        latestActivity.setStatus(0);
        Mockito.when(groupBuyActivityDao.queryGroupBuyActivityList()).thenReturn(Arrays.asList(oldActivity, latestActivity));
        Mockito.when(groupBuyActivityDao.insertGroupBuyActivity(Mockito.any(GroupBuyActivity.class))).thenReturn(1);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));
        Map<String, Object> requestDTO = new HashMap<>();
        requestDTO.put("name", "3人拼团活动");
        requestDTO.put("startTimestamp", "1893456000000");
        requestDTO.put("endTimestamp", "1924992000000");
        requestDTO.put("target", "3");
        requestDTO.put("discountId", "25120208");

        Response<Boolean> response = controller.createMarketingActivity(request, requestDTO);

        Assertions.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assertions.assertEquals(Boolean.TRUE, response.getData());
        Mockito.verify(groupBuyActivityDao, Mockito.never()).deleteGroupBuyActivityByActivityId(Mockito.anyLong());
        Mockito.verify(groupBuyActivityDao, Mockito.never()).updateGroupBuyActivityByActivityId(Mockito.any(GroupBuyActivity.class));
        Mockito.verify(groupBuyActivityDao).insertGroupBuyActivity(Mockito.argThat(activity ->
                !Objects.equals(activity.getActivityId(), 1001L) && !Objects.equals(activity.getActivityId(), 1002L)));
    }

    @Test
    public void createMarketingActivity_shouldRejectMissingNameWithoutWritingDatabase() {
        AdminController controller = new AdminController();
        IGroupBuyActivityDao groupBuyActivityDao = Mockito.mock(IGroupBuyActivityDao.class);
        IGroupBuyDiscountDao groupBuyDiscountDao = Mockito.mock(IGroupBuyDiscountDao.class);
        ReflectionTestUtils.setField(controller, "groupBuyActivityDao", groupBuyActivityDao);
        ReflectionTestUtils.setField(controller, "groupBuyDiscountDao", groupBuyDiscountDao);

        GroupBuyDiscount discount = new GroupBuyDiscount();
        discount.setDiscountId(25120208);
        discount.setMarketPlan("N");
        discount.setMarketExpr("599");
        Mockito.when(groupBuyDiscountDao.queryGroupBuyActivityDiscountByDiscountId("25120208")).thenReturn(discount);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));
        Map<String, Object> requestDTO = new HashMap<>();
        requestDTO.put("startTimestamp", "1893456000000");
        requestDTO.put("endTimestamp", "1924992000000");
        requestDTO.put("target", "3");
        requestDTO.put("discountId", "25120208");

        Response<Boolean> response = controller.createMarketingActivity(request, requestDTO);

        Assertions.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assertions.assertEquals("活动名称、开始时间、结束时间不能为空", response.getInfo());
        Assertions.assertEquals(Boolean.FALSE, response.getData());
        Mockito.verify(groupBuyActivityDao, Mockito.never()).insertGroupBuyActivity(Mockito.any(GroupBuyActivity.class));
        Mockito.verify(groupBuyActivityDao, Mockito.never()).updateGroupBuyActivityByActivityId(Mockito.any(GroupBuyActivity.class));
    }

    @Test
    public void updateMarketingActivityStatus_shouldRejectInvalidStatusPayloadWithoutWritingDatabase() {
        AdminController controller = new AdminController();
        IGroupBuyActivityDao groupBuyActivityDao = Mockito.mock(IGroupBuyActivityDao.class);
        ReflectionTestUtils.setField(controller, "groupBuyActivityDao", groupBuyActivityDao);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("username", "admin"));
        Map<String, Object> requestDTO = new HashMap<>();
        requestDTO.put("fromStatus", "bad-status");
        requestDTO.put("toStatus", "1");

        Response<Boolean> response = controller.updateMarketingActivityStatus(request, 1001L, requestDTO);

        Assertions.assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        Assertions.assertEquals("活动状态参数不合法", response.getInfo());
        Assertions.assertEquals(Boolean.FALSE, response.getData());
        Mockito.verify(groupBuyActivityDao, Mockito.never()).updateActivityStatusByActivityId(Mockito.anyLong(), Mockito.any(), Mockito.any());
        Mockito.verify(groupBuyActivityDao, Mockito.never()).queryGroupBuyActivityStatusByActivityId(Mockito.anyLong());
    }
}
