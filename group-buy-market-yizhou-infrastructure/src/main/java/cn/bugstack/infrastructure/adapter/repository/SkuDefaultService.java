package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.infrastructure.dao.ISkuDao;
import cn.bugstack.infrastructure.dao.po.Sku;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @description 默认商品初始化服务（仅在显式初始化时调用）
 */
@Component
public class SkuDefaultService {

    private static final String DEFAULT_SOURCE = "s01";
    private static final String DEFAULT_CHANNEL = "c01";

    private static final List<Sku> DEFAULT_SKU_LIST = Arrays.asList(
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1101001").goodsName("iPhone 17").originalPrice(new BigDecimal("5999.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1101002").goodsName("iPhone 17 Pro").originalPrice(new BigDecimal("8999.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1101003").goodsName("iPhone 17 Pro Max").originalPrice(new BigDecimal("9999.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1101004").goodsName("HUAWEI Mate 80").originalPrice(new BigDecimal("4699.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1101005").goodsName("HUAWEI Mate 80 Pro").originalPrice(new BigDecimal("5999.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1101006").goodsName("HUAWEI Mate X7").originalPrice(new BigDecimal("12999.00")).build(),

            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1102001").goodsName("AirPods 4").originalPrice(new BigDecimal("999.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1102002").goodsName("AirPods Pro 3").originalPrice(new BigDecimal("1899.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1102003").goodsName("AirPods Max 2").originalPrice(new BigDecimal("3999.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1102004").goodsName("HUAWEI FreeBuds Pro 5").originalPrice(new BigDecimal("1499.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1102005").goodsName("HUAWEI FreeBuds 6").originalPrice(new BigDecimal("999.00")).build(),

            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1103001").goodsName("iPad Pro").originalPrice(new BigDecimal("8999.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1103002").goodsName("iPad Air").originalPrice(new BigDecimal("4799.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1103003").goodsName("HUAWEI MatePad Pro").originalPrice(new BigDecimal("3999.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1103004").goodsName("HUAWEI MatePad Air").originalPrice(new BigDecimal("2799.00")).build(),

            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1104001").goodsName("MacBook Neo").originalPrice(new BigDecimal("4599.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1104002").goodsName("MacBook Air").originalPrice(new BigDecimal("8499.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1104003").goodsName("MacBook Pro").originalPrice(new BigDecimal("13499.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1104004").goodsName("HUAWEI MateBook Pro").originalPrice(new BigDecimal("7999.00")).build(),
            Sku.builder().source(DEFAULT_SOURCE).channel(DEFAULT_CHANNEL).goodsId("1104005").goodsName("HUAWEI MateBook Fold").originalPrice(new BigDecimal("23999.00")).build()
    );

    @Resource
    private ISkuDao skuDao;

    public Sku ensureSkuByGoodsId(String goodsId) {
        return skuDao.querySkuByGoodsId(goodsId);
    }

    public List<Sku> ensureDefaultSkus() {
        List<Sku> result = new ArrayList<>(DEFAULT_SKU_LIST.size());
        for (Sku defaultSku : DEFAULT_SKU_LIST) {
            Sku dbSku = skuDao.querySkuByGoodsId(defaultSku.getGoodsId());
            if (null == dbSku) {
                try {
                    skuDao.insertSku(withDefaultStock(defaultSku));
                } catch (DuplicateKeyException ignore) {
                    // 并发补齐场景下，唯一索引冲突可直接走后续查询
                }
                dbSku = skuDao.querySkuByGoodsId(defaultSku.getGoodsId());
            }

            if (null != dbSku) {
                result.add(dbSku);
            }
        }
        return result;
    }

    public List<Sku> getDemoSkus() {
        List<Sku> result = new ArrayList<>(DEFAULT_SKU_LIST.size());
        for (Sku sku : DEFAULT_SKU_LIST) {
            result.add(Sku.builder()
                    .source(sku.getSource())
                    .channel(sku.getChannel())
                    .goodsId(sku.getGoodsId())
                    .goodsName(sku.getGoodsName())
                    .originalPrice(sku.getOriginalPrice())
                    .stock(null == sku.getStock() ? 100 : sku.getStock())
                    .build());
        }
        return result;
    }

    private Sku withDefaultStock(Sku sku) {
        return Sku.builder()
                .source(sku.getSource())
                .channel(sku.getChannel())
                .goodsId(sku.getGoodsId())
                .goodsName(sku.getGoodsName())
                .originalPrice(sku.getOriginalPrice())
                .stock(null == sku.getStock() ? 100 : sku.getStock())
                .build();
    }

}
