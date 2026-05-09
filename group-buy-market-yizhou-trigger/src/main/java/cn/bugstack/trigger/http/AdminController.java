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
import cn.bugstack.infrastructure.dao.ISCSkuActivityDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyActivity;
import cn.bugstack.infrastructure.dao.po.GroupBuyDiscount;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.types.enums.ResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/gbm/admin/")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    private static final String REMOVED_N_TIER_EXPR = "1.99";

    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private IGroupBuyActivityDao groupBuyActivityDao;
    @Resource
    private IGroupBuyDiscountDao groupBuyDiscountDao;
    @Resource
    private ISCSkuActivityDao scSkuActivityDao;
    @Resource
    private ITradeLockOrderService tradeOrderService;
    @Resource
    private IAdminProductService adminProductService;

    @GetMapping("products")
    public Response<Map<String, Object>> queryProducts(HttpServletRequest request) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginResponse();
            }

            AdminProductListEntity productListEntity = adminProductService.queryProductList();
            List<AdminProductEntity> productList = Optional.ofNullable(productListEntity)
                    .map(AdminProductListEntity::getProductList)
                    .orElse(Collections.emptyList());
            List<Map<String, Object>> list = productList.stream().map(product -> {
                Map<String, Object> item = new HashMap<>();
                item.put("name", product.getGoodsName());
                item.put("sku", product.getGoodsId());
                item.put("stock", null == product.getStock() ? 100 : product.getStock());
                item.put("status", null == product.getStatus() ? "上架" : product.getStatus());
                item.put("price", null == product.getPrice() ? BigDecimal.ZERO : product.getPrice());
                item.put("activityId", product.getActivityId());
                item.put("activityName", null == product.getActivityName() ? "无活动" : product.getActivityName());
                item.put("activityType", null == product.getActivityType() ? "无活动" : product.getActivityType());
                item.put("activityTypeDisplay", null == product.getActivityTypeDisplay() ? "无活动" : product.getActivityTypeDisplay());
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> summary = new HashMap<>();
            summary.put("total", list.size());
            summary.put("online", list.size());
            summary.put("lowStock", 0);
            summary.put("avgPrice", list.isEmpty() ? 0 : list.stream()
                    .map(it -> (BigDecimal) it.get("price"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(list.size()), 0, BigDecimal.ROUND_HALF_UP));

            Map<String, Object> data = new HashMap<>();
            data.put("summary", summary);
            data.put("list", list);
            data.put("activityTypes", Optional.ofNullable(productListEntity)
                    .map(AdminProductListEntity::getActivityTypes)
                    .orElse(Collections.emptyList()));

            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @GetMapping("orders")
    public Response<Map<String, Object>> queryOrders(HttpServletRequest request,
                                                     @RequestParam(value = "status", required = false, defaultValue = "all") String status,
                                                     @RequestParam(value = "startDate", required = false) String startDate,
                                                     @RequestParam(value = "endDate", required = false) String endDate,
                                                     @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                     @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginResponse();
            }

            Integer dbStatus = convertOrderStatus(status);
            List<GroupBuyOrderList> orderList = null == dbStatus
                    ? groupBuyOrderListDao.queryAdminOrderList()
                    : groupBuyOrderListDao.queryAdminOrderListByStatus(dbStatus);

            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            if (null != start || null != end) {
                orderList = orderList.stream().filter(order -> {
                    Date dt = null != order.getOutTradeTime() ? order.getOutTradeTime() : order.getCreateTime();
                    if (null == dt) return false;
                    LocalDate current = dt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    if (null != start && current.isBefore(start)) return false;
                    if (null != end && current.isAfter(end)) return false;
                    return true;
                }).collect(Collectors.toList());
            }

            int safePage = null == page || page < 1 ? 1 : page;
            int safePageSize = null == pageSize || pageSize < 1 ? 10 : Math.min(pageSize, 50);
            int filteredTotal = orderList.size();
            int fromIndex = Math.min((safePage - 1) * safePageSize, filteredTotal);
            int toIndex = Math.min(fromIndex + safePageSize, filteredTotal);
            List<GroupBuyOrderList> pageList = orderList.subList(fromIndex, toIndex);

            List<Map<String, Object>> list = pageList.stream().map(order -> {
                Map<String, Object> item = new HashMap<>();
                item.put("no", order.getOutTradeNo());
                item.put("user", order.getUserId());
                item.put("product", order.getGoodsId());
                item.put("amount", null == order.getOriginalPrice() || null == order.getDeductionPrice() ? BigDecimal.ZERO : order.getOriginalPrice().subtract(order.getDeductionPrice()));
                item.put("status", convertOrderStatusText(order.getStatus()));
                item.put("orderType", "DIRECT".equalsIgnoreCase(String.valueOf(order.getTeamId())) ? "单独购买" : "拼团购买");
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> summary = new HashMap<>();
            summary.put("total", Optional.ofNullable(groupBuyOrderListDao.queryAdminOrderCountAll()).orElse(0));
            summary.put("waiting", Optional.ofNullable(groupBuyOrderListDao.queryAdminOrderCountByStatus(0)).orElse(0));
            summary.put("paid", Optional.ofNullable(groupBuyOrderListDao.queryAdminOrderCountByStatus(1)).orElse(0));
            summary.put("canceled", Optional.ofNullable(groupBuyOrderListDao.queryAdminOrderCountByStatus(2)).orElse(0));

            Map<String, Object> data = new HashMap<>();
            data.put("summary", summary);
            data.put("list", list);
            data.put("page", safePage);
            data.put("pageSize", safePageSize);
            data.put("filteredTotal", filteredTotal);

            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @GetMapping("marketing/activities")
    public Response<Map<String, Object>> queryMarketingActivities(HttpServletRequest request) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginResponse();
            }

            List<GroupBuyActivity> activityList = groupBuyActivityDao.queryGroupBuyActivityList();
            List<GroupBuyDiscount> discountList = Optional.ofNullable(groupBuyDiscountDao.queryGroupBuyDiscountList()).orElse(Collections.emptyList());
            List<GroupBuyDiscount> availableDiscountList = discountList.stream()
                    .filter(this::isAvailableDiscountTier)
                    .collect(Collectors.toList());
            Map<String, GroupBuyDiscount> discountMap = discountList.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            discount -> String.valueOf(discount.getDiscountId()),
                            discount -> discount,
                            (oldValue, newValue) -> oldValue
                    ));

            List<Map<String, Object>> list = activityList.stream().map(activity -> {
                String normalizedName = normalizeActivityName(activity);
                GroupBuyDiscount discount = discountMap.get(String.valueOf(activity.getDiscountId()));
                String discountPlanText = convertDiscountPlanText(discount);
                Map<String, Object> item = new HashMap<>();
                item.put("activityId", activity.getActivityId());
                item.put("name", normalizedName);
                item.put("type", convertActivityTypeText(activity) + " / " + discountPlanText);
                item.put("groupTypeText", convertGroupTypeText(activity.getGroupType()));
                item.put("discountPlan", discountPlanText);
                item.put("discountId", activity.getDiscountId());
                item.put("start", activity.getStartTime());
                item.put("end", activity.getEndTime());
                item.put("status", convertActivityStatusText(activity.getStatus()));
                item.put("statusCode", activity.getStatus());
                item.put("target", activity.getTarget());
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> summary = new HashMap<>();
            summary.put("total", list.size());
            summary.put("active", activityList.stream().filter(a -> Objects.equals(a.getStatus(), 1)).count());
            summary.put("pending", activityList.stream().filter(a -> Objects.equals(a.getStatus(), 0)).count());
            summary.put("closed", activityList.stream().filter(a -> Objects.equals(a.getStatus(), 2) || Objects.equals(a.getStatus(), 3)).count());

            Map<String, Object> data = new HashMap<>();
            data.put("summary", summary);
            data.put("list", list);
            data.put("discountTypes", availableDiscountList.stream()
                    .map(this::convertDiscountPlanText)
                    .distinct()
                    .collect(Collectors.toList()));
            data.put("discountOptions", availableDiscountList.stream().map(discount -> {
                Map<String, Object> option = new HashMap<>();
                option.put("discountId", discount.getDiscountId());
                option.put("discountName", discount.getDiscountName());
                option.put("marketPlan", discount.getMarketPlan());
                option.put("marketPlanText", convertDiscountPlanText(discount));
                option.put("marketExpr", discount.getMarketExpr());
                return option;
            }).collect(Collectors.toList()));

            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @DeleteMapping("orders/{outTradeNo}")
    public Response<Boolean> deleteOrder(HttpServletRequest request,
                                         @PathVariable("outTradeNo") String outTradeNo,
                                         @RequestParam("userId") String userId) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginBooleanResponse();
            }

            String safeOutTradeNo = null == outTradeNo ? "" : outTradeNo.trim();
            String safeUserId = null == userId ? "" : userId.trim();
            if (safeOutTradeNo.isEmpty() || safeUserId.isEmpty()) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("订单号和用户ID不能为空")
                        .data(false)
                        .build();
            }

            MarketPayOrderEntity noPayOrder = tradeOrderService.queryNoPayMarketPayOrderByOutTradeNo(safeUserId, safeOutTradeNo);
            if (null == noPayOrder) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("订单不存在或不是待支付订单")
                        .data(false)
                        .build();
            }

            boolean canceled = tradeOrderService.cancelNoPayMarketPayOrder(safeUserId, safeOutTradeNo);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(canceled ? ResponseCode.SUCCESS.getInfo() : "订单取消失败")
                    .data(canceled)
                    .build();
        } catch (Exception e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @PostMapping("orders/batch-delete")
    public Response<Map<String, Object>> batchDeleteOrders(HttpServletRequest request,
                                                           @RequestBody Map<String, Object> requestDTO) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginResponse();
            }

            Object ordersObj = null == requestDTO ? null : requestDTO.get("orders");
            if (!(ordersObj instanceof List)) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("orders参数不能为空")
                        .build();
            }

            List<?> orders = (List<?>) ordersObj;
            int successCount = 0;
            List<Map<String, String>> failedItems = new ArrayList<>();

            for (Object orderObj : orders) {
                if (!(orderObj instanceof Map)) {
                    continue;
                }

                Map<?, ?> order = (Map<?, ?>) orderObj;
                String userId = null == order.get("userId") ? "" : String.valueOf(order.get("userId")).trim();
                String outTradeNo = null == order.get("outTradeNo") ? "" : String.valueOf(order.get("outTradeNo")).trim();
                if (userId.isEmpty() || outTradeNo.isEmpty()) {
                    Map<String, String> fail = new HashMap<>();
                    fail.put("userId", userId);
                    fail.put("outTradeNo", outTradeNo);
                    fail.put("reason", "参数缺失");
                    failedItems.add(fail);
                    continue;
                }

                MarketPayOrderEntity noPayOrder = tradeOrderService.queryNoPayMarketPayOrderByOutTradeNo(userId, outTradeNo);
                if (null == noPayOrder) {
                    Map<String, String> fail = new HashMap<>();
                    fail.put("userId", userId);
                    fail.put("outTradeNo", outTradeNo);
                    fail.put("reason", "订单不存在或不是待支付订单");
                    failedItems.add(fail);
                    continue;
                }

                boolean canceled = tradeOrderService.cancelNoPayMarketPayOrder(userId, outTradeNo);
                if (canceled) {
                    successCount += 1;
                } else {
                    Map<String, String> fail = new HashMap<>();
                    fail.put("userId", userId);
                    fail.put("outTradeNo", outTradeNo);
                    fail.put("reason", "订单取消失败");
                    failedItems.add(fail);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("requestedCount", orders.size());
            data.put("successCount", successCount);
            data.put("failCount", failedItems.size());
            data.put("failedItems", failedItems);

            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @GetMapping("users")
    public Response<Map<String, Object>> queryUsers(HttpServletRequest request,
                                                    @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
                                                    @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                    @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginResponse();
            }

            String safeKeyword = null == keyword ? "" : keyword.trim();
            int total = Optional.ofNullable(groupBuyOrderListDao.queryAdminUserCount(safeKeyword)).orElse(0);
            List<Map<String, Object>> list = total <= 0
                    ? Collections.emptyList()
                    : Optional.ofNullable(groupBuyOrderListDao.queryAdminUserList(safeKeyword, 0, total)).orElse(Collections.emptyList());

            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("page", 1);
            data.put("pageSize", total <= 0 ? 10 : total);
            data.put("total", total);

            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @GetMapping("users/{userId}/orders")
    public Response<Map<String, Object>> queryUserOrders(HttpServletRequest request,
                                                         @PathVariable("userId") String userId,
                                                         @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                         @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginResponse();
            }

            String safeUserId = null == userId ? "" : userId.trim();
            if (safeUserId.isEmpty()) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户ID不能为空")
                        .build();
            }

            int safePage = null == page || page < 1 ? 1 : page;
            int safePageSize = null == pageSize || pageSize < 1 ? 10 : Math.min(pageSize, 50);
            int offset = (safePage - 1) * safePageSize;

            List<GroupBuyOrderList> orderList = Optional.ofNullable(groupBuyOrderListDao.queryAdminOrderListByUserId(safeUserId, offset, safePageSize)).orElse(Collections.emptyList());
            int total = Optional.ofNullable(groupBuyOrderListDao.queryAdminOrderCountByUserId(safeUserId)).orElse(0);

            List<Map<String, Object>> list = orderList.stream().map(order -> {
                Map<String, Object> item = new HashMap<>();
                item.put("no", order.getOutTradeNo());
                item.put("product", order.getGoodsId());
                item.put("amount", null == order.getOriginalPrice() || null == order.getDeductionPrice() ? BigDecimal.ZERO : order.getOriginalPrice().subtract(order.getDeductionPrice()));
                item.put("status", convertOrderStatusText(order.getStatus()));
                item.put("orderType", "DIRECT".equalsIgnoreCase(String.valueOf(order.getTeamId())) ? "单独购买" : "拼团购买");
                item.put("createTime", order.getCreateTime());
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("page", safePage);
            data.put("pageSize", safePageSize);
            data.put("total", total);

            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @PostMapping("ops/orders/repair/clear-inprogress")
    public Response<Map<String, Object>> clearInProgressAbnormalOrder(HttpServletRequest request, @RequestBody Map<String, Object> requestDTO) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginResponse();
            }

            String userId = String.valueOf(requestDTO.get("userId")).trim();
            String goodsId = String.valueOf(requestDTO.get("goodsId")).trim();
            if (userId.isEmpty() || goodsId.isEmpty()) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("userId、goodsId不能为空")
                        .build();
            }

            int affectedCount = groupBuyOrderListDao.clearInProgressAbnormalByUserIdGoodsId(userId, goodsId);

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("affectedCount", affectedCount);
            data.put("userId", userId);
            data.put("goodsId", goodsId);
            data.put("message", affectedCount > 0 ? "清理完成" : "无可清理记录");

            logger.warn("运维修复-清理进行中拼团 userId:{} goodsId:{} affectedCount:{}", userId, goodsId, affectedCount);

            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            logger.error("运维修复失败 requestDTO:{}", requestDTO, e);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @PostMapping("products")
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> createProduct(HttpServletRequest request, @RequestBody Map<String, Object> requestDTO) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginBooleanResponse();
            }

            Map<String, Object> safeRequestDTO = null == requestDTO ? Collections.emptyMap() : requestDTO;
            String goodsId = readText(safeRequestDTO.get("sku"));
            String goodsName = readText(safeRequestDTO.get("name"));
            String priceText = readText(safeRequestDTO.get("price"));
            Long activityId = parseActivityId(safeRequestDTO.get("activityId"));

            if (goodsId.isEmpty() || goodsName.isEmpty() || priceText.isEmpty()) {
                return illegalBooleanResponse("商品名称、SKU、售价不能为空");
            }

            if (goodsId.length() > 16) {
                return illegalBooleanResponse("SKU长度不能超过16位");
            }

            BigDecimal price = parsePrice(priceText);
            if (null == price) {
                return illegalBooleanResponse("售价格式不正确");
            }
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                return illegalBooleanResponse("售价不能为负数");
            }

            boolean success = adminProductService.createProduct(goodsId, goodsName, price, activityId);
            if (!success && null != activityId) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("当前无可用活动，请先在后台创建活动")
                        .data(false)
                        .build();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(success)
                    .build();
        } catch (DuplicateKeyException e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("SKU已存在，请更换后重试")
                    .data(false)
                    .build();
        } catch (Exception e) {
            logger.error("新增商品失败 requestDTO:{}", requestDTO, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @PutMapping("products/{goodsId}")
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> updateProduct(HttpServletRequest request, @PathVariable("goodsId") String goodsId, @RequestBody Map<String, Object> requestDTO) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginBooleanResponse();
            }

            Map<String, Object> safeRequestDTO = null == requestDTO ? Collections.emptyMap() : requestDTO;
            String safeGoodsId = readText(goodsId);
            String goodsName = readText(safeRequestDTO.get("name"));
            String priceText = readText(safeRequestDTO.get("price"));
            if (safeGoodsId.isEmpty() || goodsName.isEmpty() || priceText.isEmpty()) {
                return illegalBooleanResponse("商品ID、商品名称、售价不能为空");
            }

            BigDecimal price = parsePrice(priceText);
            if (null == price) {
                return illegalBooleanResponse("售价格式不正确");
            }
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                return illegalBooleanResponse("售价不能为负数");
            }

            Long activityId = parseActivityId(safeRequestDTO.get("activityId"));
            boolean success = adminProductService.updateProduct(safeGoodsId, goodsName, price, activityId);
            if (!success && null != activityId) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("绑定活动失败，请重试")
                        .data(false)
                        .build();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(success)
                    .build();
        } catch (Exception e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @DeleteMapping("products/{goodsId}")
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> deleteProduct(HttpServletRequest request, @PathVariable("goodsId") String goodsId) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginBooleanResponse();
            }

            String safeGoodsId = readText(goodsId);
            if (safeGoodsId.isEmpty()) {
                return illegalBooleanResponse("商品ID不能为空");
            }

            boolean success = adminProductService.deleteProduct(safeGoodsId);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(success)
                    .build();
        } catch (Exception e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @PostMapping({"products/initialize-demo", "products/initialize-default"})
    @Transactional(rollbackFor = Exception.class)
    public Response<Map<String, Object>> initializeDefaultProducts(HttpServletRequest request) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginResponse();
            }

            DefaultProductInitializeEntity result = adminProductService.initializeDefaultProducts();
            if (null == result || result.getTotal() <= 0) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("默认商品配置为空")
                        .build();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("total", result.getTotal());
            data.put("inserted", result.getInserted());
            data.put("defaultActivityId", result.getDefaultActivityId());

            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            logger.error("初始化默认商品失败", e);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @PostMapping("marketing/activities")
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> createMarketingActivity(HttpServletRequest request, @RequestBody Map<String, Object> requestDTO) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginBooleanResponse();
            }

            Map<String, Object> safeRequestDTO = null == requestDTO ? Collections.emptyMap() : requestDTO;
            String name = readText(safeRequestDTO.get("name"));
            String startTimestampText = readText(safeRequestDTO.get("startTimestamp"));
            String endTimestampText = readText(safeRequestDTO.get("endTimestamp"));
            int target = parseTargetCount(safeRequestDTO.get("target"));
            String discountId = parseDiscountId(safeRequestDTO.get("discountId"));

            if (target < 2) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动类型(拼团人数)不合法，支持2-100人")
                        .data(false)
                        .build();
            }

            if (null == discountId) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("请选择优惠类型")
                        .data(false)
                        .build();
            }

            GroupBuyDiscount selectedDiscount = groupBuyDiscountDao.queryGroupBuyActivityDiscountByDiscountId(discountId);
            if (null == selectedDiscount) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("优惠类型不存在，请刷新后重试")
                        .data(false)
                        .build();
            }

            if (!isAvailableDiscountTier(selectedDiscount)) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("当前优惠档位已下线，请选择其他优惠类型")
                        .data(false)
                        .build();
            }

            if (name.isEmpty() || startTimestampText.isEmpty() || endTimestampText.isEmpty()) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动名称、开始时间、结束时间不能为空")
                        .data(false)
                        .build();
            }

            if (name.length() > 64) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动名称长度不能超过64")
                        .data(false)
                        .build();
            }

            long startTimestamp;
            long endTimestamp;
            try {
                startTimestamp = Long.parseLong(startTimestampText);
                endTimestamp = Long.parseLong(endTimestampText);
            } catch (NumberFormatException e) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动时间参数格式错误")
                        .data(false)
                        .build();
            }

            if (startTimestamp >= endTimestamp) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动开始时间必须早于结束时间")
                        .data(false)
                        .build();
            }

            List<GroupBuyActivity> activityList = Optional.ofNullable(groupBuyActivityDao.queryGroupBuyActivityList()).orElse(Collections.emptyList());

            GroupBuyActivity activity = new GroupBuyActivity();
            activity.setActivityId(generateActivityId(activityList));
            activity.setActivityName(name.isEmpty() ? target + "人拼团活动" : name);
            activity.setDiscountId(discountId);
            activity.setGroupType(0);
            activity.setTakeLimitCount(1);
            activity.setTarget(target);
            activity.setValidTime(60);
            activity.setStatus(0);
            activity.setStartTime(new Date(startTimestamp));
            activity.setEndTime(new Date(endTimestamp));
            activity.setTagId(null);
            activity.setTagScope(null);

            int count = groupBuyActivityDao.insertGroupBuyActivity(activity);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(count > 0)
                    .build();
        } catch (Exception e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @PutMapping("marketing/activities/{activityId}/status")
    public Response<Boolean> updateMarketingActivityStatus(HttpServletRequest request, @PathVariable("activityId") Long activityId, @RequestBody Map<String, Object> requestDTO) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginBooleanResponse();
            }

            if (null == activityId || activityId <= 0) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动ID不合法")
                        .data(false)
                        .build();
            }

            Map<String, Object> safeRequestDTO = null == requestDTO ? Collections.emptyMap() : requestDTO;
            Integer fromStatus = parseActivityStatus(safeRequestDTO.get("fromStatus"));
            Integer toStatus = parseActivityStatus(safeRequestDTO.get("toStatus"));
            if (null == fromStatus || null == toStatus) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动状态参数不合法")
                        .data(false)
                        .build();
            }

            if (!isValidStatusTransition(fromStatus, toStatus)) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("非法状态流转")
                        .data(false)
                        .build();
            }

            Integer dbStatus = groupBuyActivityDao.queryGroupBuyActivityStatusByActivityId(activityId);
            if (null == dbStatus || !Objects.equals(dbStatus, fromStatus)) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("当前活动状态已变化，请刷新后重试")
                        .data(false)
                        .build();
            }

            GroupBuyActivity activity = groupBuyActivityDao.queryGroupBuyActivityByActivityId(activityId);
            if (null == activity) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动不存在")
                        .data(false)
                        .build();
            }

            if (Objects.equals(toStatus, 1)) {
                Date now = new Date();
                if (null == activity.getStartTime() || null == activity.getEndTime()) {
                    return Response.<Boolean>builder()
                            .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                            .info("活动时间配置不完整")
                            .data(false)
                            .build();
                }
                if (now.before(activity.getStartTime()) || now.after(activity.getEndTime())) {
                    return Response.<Boolean>builder()
                            .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                            .info("活动不在可上线时间窗口内")
                            .data(false)
                            .build();
                }
            }

            int count = groupBuyActivityDao.updateActivityStatusByActivityId(activityId, fromStatus, toStatus);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(count > 0)
                    .build();
        } catch (Exception e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @DeleteMapping("marketing/activities/{activityId}")
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> deleteMarketingActivity(HttpServletRequest request, @PathVariable("activityId") Long activityId) {
        try {
            if (!hasLoginCookie(request)) {
                return notLoginBooleanResponse();
            }

            if (null == activityId || activityId <= 0) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动ID不合法")
                        .data(false)
                        .build();
            }

            GroupBuyActivity activity = groupBuyActivityDao.queryGroupBuyActivityByActivityId(activityId);
            if (null == activity) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动不存在或已删除")
                        .data(false)
                        .build();
            }

            if (Objects.equals(activity.getStatus(), 1)) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("活动进行中，请先结束后再删除")
                        .data(false)
                        .build();
            }

            scSkuActivityDao.deleteSCSkuActivityByActivityId(activityId);
            int count = groupBuyActivityDao.deleteGroupBuyActivityByActivityId(activityId);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(count > 0)
                    .build();
        } catch (Exception e) {
            logger.error("删除营销活动失败 activityId:{}", activityId, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    private Integer convertOrderStatus(String status) {
        if ("待支付".equals(status)) return 0;
        if ("已支付".equals(status)) return 1;
        if ("已取消".equals(status)) return 2;
        return null;
    }

    private String convertOrderStatusText(Integer status) {
        if (Objects.equals(status, 1)) return "已支付";
        if (Objects.equals(status, 0)) return "待支付";
        if (Objects.equals(status, 2)) return "已取消";
        return "未知";
    }

    private String convertActivityStatusText(Integer status) {
        if (Objects.equals(status, 1)) return "进行中";
        if (Objects.equals(status, 0)) return "待上线";
        if (Objects.equals(status, 2)) return "已结束";
        if (Objects.equals(status, 3)) return "已废弃";
        return "未知";
    }

    private String convertActivityTypeText(GroupBuyActivity activity) {
        Integer target = null == activity ? null : activity.getTarget();
        if (null == target || target <= 0) return "拼团";
        return target + "人拼团";
    }

    private String convertGroupTypeText(Integer groupType) {
        if (Objects.equals(groupType, 0)) return "自动成团";
        if (Objects.equals(groupType, 1)) return "达成目标成团";
        return "未知";
    }

    private String convertDiscountPlanText(GroupBuyDiscount discount) {
        if (null == discount) return "未知优惠";

        String marketPlan = discount.getMarketPlan();
        if ("ZJ".equalsIgnoreCase(marketPlan)) return "直减";
        if ("MJ".equalsIgnoreCase(marketPlan)) return "满减";
        if ("ZK".equalsIgnoreCase(marketPlan)) return "折扣";
        if ("N".equalsIgnoreCase(marketPlan)) {
            String expr = null == discount.getMarketExpr() ? "" : discount.getMarketExpr().trim();
            if (!expr.isEmpty()) {
                return "N" + expr;
            }
            return "N元购";
        }
        return null == marketPlan || marketPlan.trim().isEmpty() ? "未知优惠" : marketPlan;
    }

    private boolean isValidStatusTransition(Integer fromStatus, Integer toStatus) {
        return (Objects.equals(fromStatus, 0) && Objects.equals(toStatus, 1))
                || (Objects.equals(fromStatus, 1) && Objects.equals(toStatus, 2))
                || (Objects.equals(fromStatus, 2) && Objects.equals(toStatus, 1));
    }

    private Integer parseActivityStatus(Object statusValue) {
        String text = readText(statusValue);
        if (text.isEmpty()) return null;
        try {
            int status = Integer.parseInt(text);
            return status >= 0 && status <= 3 ? status : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String dateText) {
        if (null == dateText || dateText.trim().isEmpty()) return null;
        return LocalDate.parse(dateText);
    }

    private String readText(Object value) {
        if (null == value) return "";
        String text = String.valueOf(value).trim();
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private BigDecimal parsePrice(String priceText) {
        try {
            return new BigDecimal(priceText);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasLoginCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (null == cookies || cookies.length == 0) return false;
        for (Cookie cookie : cookies) {
            if ("username".equals(cookie.getName()) && null != cookie.getValue() && !cookie.getValue().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Long generateActivityId(List<GroupBuyActivity> activityList) {
        Set<Long> existedActivityIds = Optional.ofNullable(activityList).orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .map(GroupBuyActivity::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long maxActivityId = existedActivityIds.stream().mapToLong(Long::longValue).max().orElse(0L);
        long candidate = Math.max(maxActivityId + 1, System.currentTimeMillis() % 100000000);
        while (existedActivityIds.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private Long parseActivityId(Object activityIdValue) {
        if (null == activityIdValue) return null;
        String text = String.valueOf(activityIdValue).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) return null;
        try {
            long value = Long.parseLong(text);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private int parseTargetCount(Object targetValue) {
        String text = null == targetValue ? "" : String.valueOf(targetValue).trim();
        if (text.isEmpty()) {
            return -1;
        }

        int target;
        try {
            target = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }

        // Support dynamic activity types (group size), not only fixed presets.
        if (target < 2 || target > 100) {
            return -1;
        }
        return target;
    }

    private String parseDiscountId(Object discountIdValue) {
        if (null == discountIdValue) return null;
        String text = String.valueOf(discountIdValue).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) return null;
        return text;
    }

    private boolean isAvailableDiscountTier(GroupBuyDiscount discount) {
        if (null == discount) return false;
        if (!"N".equalsIgnoreCase(discount.getMarketPlan())) return true;
        String expr = null == discount.getMarketExpr() ? "" : discount.getMarketExpr().trim();
        return !REMOVED_N_TIER_EXPR.equals(expr);
    }

    private String normalizeActivityName(GroupBuyActivity activity) {
        if (null == activity) return "-";
        String activityName = null == activity.getActivityName() ? "" : activity.getActivityName().trim();
        if (activityName.contains("手写MyBatis")) {
            return activity.getTarget() + "人拼团活动";
        }
        return activityName;
    }

    private Response<Map<String, Object>> notLoginResponse() {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("未登录或登录已过期")
                .build();
    }

    private Response<Boolean> notLoginBooleanResponse() {
        return Response.<Boolean>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("未登录或登录已过期")
                .data(false)
                .build();
    }

    private Response<Boolean> illegalBooleanResponse(String info) {
        return Response.<Boolean>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(info)
                .data(false)
                .build();
    }
}
