ALTER TABLE `sku`
    ADD COLUMN `stock` int(11) unsigned NOT NULL DEFAULT '100' COMMENT '库存' AFTER `original_price`;
