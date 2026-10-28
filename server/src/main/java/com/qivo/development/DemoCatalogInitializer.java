package com.qivo.development;

/** 保留旧运行入口；商品目录的实际实现统一到 CatalogDataInitializer。 */
public final class DemoCatalogInitializer {
    private DemoCatalogInitializer() {}

    public static void main(String[] args) throws Exception {
        CatalogDataInitializer.main(args);
    }
}
