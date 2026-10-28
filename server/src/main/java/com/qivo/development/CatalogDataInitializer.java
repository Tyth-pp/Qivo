package com.qivo.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.dto.SkuSelection;
import com.qivo.dto.SkuSpecificationDefinition;
import com.qivo.enums.ProductSpecMode;
import com.qivo.enums.SkuStatus;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 仅显式执行的商品目录命令；现有商品按固定规格编码复用，不随应用启动。 */
public final class CatalogDataInitializer {
    private static final String CODE_PREFIX = "QIVO-CATALOG-V2-";
    private static final String IMAGE_PREFIX = "/upload/products/";
    private static final String EVENT_PREFIX = "catalog-v2:";
    private static final Set<String> BLOCKED =
            Set.of("mall", "mysql", "information_schema", "performance_schema", "sys");
    private static final ObjectMapper JSON = new ObjectMapper();

    private CatalogDataInitializer() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Set.of("seed", "cleanup").contains(args[0]))
            throw new IllegalArgumentException("用法：CatalogDataInitializer seed|cleanup");
        try (Connection connection = DriverManager.getConnection(
                required("QIVO_DEV_DB_URL"), required("DB_USERNAME"), required("DB_PASSWORD"))) {
            run(
                    connection,
                    required("QIVO_DEV_DATABASE_CONFIRM"),
                    Path.of(required("QIVO_DEMO_UPLOAD_DIR")),
                    Path.of(required("QIVO_DEMO_ASSET_DIR")),
                    args[0]);
        }
    }

    public static Catalog catalog() throws Exception {
        try (var input = CatalogDataInitializer.class.getResourceAsStream("/db/development/商品目录.json")) {
            if (input == null) throw new IllegalStateException("缺少商品目录清单");
            Catalog catalog = JSON.readValue(input, Catalog.class);
            validate(catalog);
            return catalog;
        }
    }

    public static void run(Connection connection, String target, Path upload, Path assets, String action)
            throws Exception {
        validateTarget(connection, target);
        if (!Set.of("seed", "cleanup").contains(action))
            throw new IllegalArgumentException("未知商品目录操作");
        if (!connection.getAutoCommit()) throw new IllegalStateException("命令需要独立数据库连接");
        for (String table : List.of("product_category", "product", "product_sku", "stock_movement")) {
            try (var rows = connection.getMetaData().getTables(
                    connection.getCatalog(), null, table, new String[] {"TABLE"})) {
                if (!rows.next()) throw new IllegalStateException("缺少业务表：" + table);
            }
        }
        Catalog catalog = catalog();
        String lock = "qivo:catalog-data:" + target;
        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
            statement.setString(1, lock);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1)
                    throw new IllegalStateException("另一个商品目录命令正在执行");
            }
        }
        List<Path> created = new ArrayList<>();
        try {
            if (action.equals("seed")) copyImages(catalog, upload, assets, created);
            connection.setAutoCommit(false);
            if (action.equals("seed")) seed(connection, catalog);
            else cleanup(connection, catalog);
            connection.commit();
        } catch (Exception exception) {
            if (!connection.getAutoCommit()) connection.rollback();
            for (Path image : created) Files.deleteIfExists(image);
            throw exception;
        } finally {
            connection.setAutoCommit(true);
            try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, lock);
                statement.executeQuery().close();
            }
        }
    }

    /** 统一首次初始化复用调用方事务，失败时由调用方回滚并移除本次新图片。 */
    static void seedWithinTransaction(
            Connection connection, Path upload, Path assets, List<Path> created) throws Exception {
        if (connection.getAutoCommit())
            throw new IllegalStateException("商品首次初始化必须加入调用方事务");
        Catalog catalog = catalog();
        copyImages(catalog, upload, assets, created);
        seed(connection, catalog);
    }

    private static void seed(Connection connection, Catalog catalog) throws Exception {
        boolean alreadyExpanded = scalar(connection,
                "SELECT COUNT(*) FROM product_sku WHERE sku_code LIKE 'QIVO-CATALOG-V2-%'") > 0;
        Map<String, Long> categories = resolveCategories(connection, catalog.categories());
        int inserted = 0, retained = 0;
        for (ProductSeed product : catalog.products()) {
            long categoryId = categories.get(product.category());
            ProductMatch match = findProduct(connection, product);
            if (match == null) {
                if (scalar(connection, "SELECT COUNT(*) FROM product WHERE name=? OR main_image=?",
                        product.name(), image(product.image())) > 0)
                    throw new IllegalStateException("同名或同图片商品已存在，拒绝接管：" + product.name());
                insertProduct(connection, product, categoryId);
                inserted++;
            } else {
                verifyProduct(connection, product, match);
                try (var statement = connection.prepareStatement("SELECT category_id FROM product WHERE id=? FOR UPDATE")) {
                    statement.setLong(1, match.id());
                    try (var rows = statement.executeQuery()) {
                        rows.next();
                        long currentCategory = rows.getLong(1);
                        if (currentCategory != categoryId) {
                            if (alreadyExpanded || !categories.containsValue(currentCategory))
                                throw new IllegalStateException("商品分类已被手工调整，拒绝覆盖：" + product.name());
                            update(connection, "UPDATE product SET category_id=? WHERE id=?", categoryId, match.id());
                        }
                    }
                }
                retained++;
            }
        }
        System.out.println("商品目录已就绪：新增 " + inserted + " 件，保留 " + retained + " 件。");
    }

    private static Map<String, Long> resolveCategories(Connection connection, List<CategorySeed> seeds)
            throws Exception {
        Map<String, Long> result = new LinkedHashMap<>();
        for (CategorySeed category : seeds) {
            long parent = category.parent() == null ? 0 : Objects.requireNonNull(result.get(category.parent()));
            List<String> names = new ArrayList<>(category.previousNames());
            names.add(category.name());
            String slots = names.stream().map(name -> "?").collect(Collectors.joining(","));
            Long id = null;
            String currentName = null;
            long currentParent = 0;
            try (var statement = connection.prepareStatement(
                    "SELECT id,name,parent_id FROM product_category WHERE name IN (" + slots + ") FOR UPDATE")) {
                for (int i = 0; i < names.size(); i++) statement.setString(i + 1, names.get(i));
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        if (id != null) throw new IllegalStateException("分类名称冲突：" + category.name());
                        id = rows.getLong(1);
                        currentName = rows.getString(2);
                        currentParent = rows.getLong(3);
                    }
                }
            }
            if (id == null) {
                try (var statement = connection.prepareStatement(
                        "INSERT INTO product_category(name,parent_id,sort_order,icon,status) VALUES (?,?,?,?,1)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, category.name());
                    statement.setLong(2, parent);
                    statement.setInt(3, category.order());
                    statement.setString(4, category.image() == null ? null : image(category.image()));
                    statement.executeUpdate();
                    id = generatedId(statement);
                }
            } else if (!category.name().equals(currentName) || currentParent != parent) {
                if (!category.previousNames().contains(currentName)
                        || (currentParent != 0 && currentParent != parent))
                    throw new IllegalStateException("分类层级不符合已确认的整理范围：" + category.name());
                update(connection, "UPDATE product_category SET name=?,parent_id=?,sort_order=? WHERE id=?",
                        category.name(), parent, category.order(), id);
            }
            result.put(category.key(), id);
        }
        return result;
    }

    private static ProductMatch findProduct(Connection connection, ProductSeed product) throws Exception {
        Set<Long> primary = productIds(connection, product.skus().stream().map(s -> code(product, s)).toList());
        Set<Long> aliases = productIds(connection, product.aliases());
        Set<Long> all = new HashSet<>(primary);
        all.addAll(aliases);
        if (all.isEmpty()) return null;
        if (all.size() != 1 || (!primary.isEmpty() && !aliases.isEmpty()))
            throw new IllegalStateException("商品规格编码归属冲突：" + product.name());
        return new ProductMatch(all.iterator().next(), !primary.isEmpty());
    }

    private static Set<Long> productIds(Connection connection, List<String> codes) throws Exception {
        Set<Long> result = new HashSet<>();
        for (String code : codes) {
            try (var statement = connection.prepareStatement("SELECT product_id FROM product_sku WHERE sku_code=?")) {
                statement.setString(1, code);
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) result.add(rows.getLong(1));
                }
            }
        }
        return result;
    }

    private static void verifyProduct(Connection connection, ProductSeed product, ProductMatch match)
            throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT name,main_image,spec_mode FROM product WHERE id=? FOR UPDATE")) {
            statement.setLong(1, match.id());
            try (var rows = statement.executeQuery()) {
                String expectedImage = match.owned() ? image(product.image()) : product.legacyImage();
                if (!rows.next() || !product.name().equals(rows.getString(1))
                        || !Objects.equals(expectedImage, rows.getString(2))
                        || !mode(product).name().equals(rows.getString(3)))
                    throw new IllegalStateException("商品名称、图片或规格模式不匹配，拒绝覆盖：" + product.name());
            }
        }
        Map<String, String> expected = new HashMap<>();
        for (int i = 0; i < product.skus().size(); i++) {
            SkuSeed sku = product.skus().get(i);
            expected.put(match.owned() ? code(product, sku) : product.aliases().get(i), specKey(sku));
        }
        int count = 0;
        try (var statement = connection.prepareStatement(
                "SELECT sku_code,spec_key FROM product_sku WHERE product_id=? ORDER BY id FOR UPDATE")) {
            statement.setLong(1, match.id());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    count++;
                    if (!Objects.equals(expected.get(rows.getString(1)), rows.getString(2)))
                        throw new IllegalStateException("商品规格已变化，拒绝覆盖：" + product.name());
                }
            }
        }
        if (count != expected.size())
            throw new IllegalStateException("商品规格不完整，拒绝覆盖：" + product.name());
    }

    private static void insertProduct(Connection connection, ProductSeed product, long categoryId)
            throws Exception {
        BigDecimal minimum = product.skus().stream().filter(s -> s.status() == SkuStatus.ENABLED)
                .map(SkuSeed::price).min(BigDecimal::compareTo).orElseThrow();
        int stock = product.skus().stream().filter(s -> s.status() == SkuStatus.ENABLED).mapToInt(SkuSeed::stock).sum();
        long productId;
        try (var statement = connection.prepareStatement(
                "INSERT INTO product(name,description,price,stock,status,category_id,main_image,sales,spec_mode,spec_definition)"
                        + " VALUES (?,?,?,?,?,?,?,0,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, product.name(), product.description(), minimum, stock, product.status(),
                    categoryId, image(product.image()), mode(product).name(), JSON.writeValueAsString(product.definitions()));
            statement.executeUpdate();
            productId = generatedId(statement);
        }
        for (SkuSeed sku : product.skus()) {
            long skuId;
            try (var statement = connection.prepareStatement(
                    "INSERT INTO product_sku(product_id,sku_code,spec_key,spec_values,price,stock,status,image)"
                            + " VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, productId, code(product, sku), specKey(sku), JSON.writeValueAsString(sku.selections()),
                        sku.price(), sku.stock(), sku.status().name(), image(product.image()));
                statement.executeUpdate();
                skuId = generatedId(statement);
            }
            update(connection,
                    "INSERT INTO stock_movement(product_id,sku_id,event_key,kind,delta,stock_before,stock_after,actor_domain)"
                            + " VALUES (?,?,?,'INITIAL',?,0,?,'SYSTEM')",
                    productId, skuId, EVENT_PREFIX + code(product, sku), sku.stock(), sku.stock());
        }
    }

    private static void cleanup(Connection connection, Catalog catalog) throws Exception {
        Set<String> known = catalog.products().stream().flatMap(p -> p.skus().stream().map(s -> code(p, s)))
                .collect(Collectors.toSet());
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT sku_code FROM product_sku WHERE sku_code LIKE 'QIVO-CATALOG-V2-%'")) {
            while (rows.next()) {
                if (!known.contains(rows.getString(1))) throw new IllegalStateException("存在未知目录规格，拒绝扩大清理范围");
            }
        }
        List<Long> removable = new ArrayList<>();
        for (ProductSeed product : catalog.products()) {
            ProductMatch match = findProduct(connection, product);
            if (match == null || !match.owned()) continue;
            verifyProduct(connection, product, match);
            for (String table : List.of("cart", "order_item", "product_collect", "product_comment", "product_img")) {
                try (var statement = connection.prepareStatement(
                        "SELECT id FROM `" + table + "` WHERE product_id=? FOR UPDATE")) {
                    statement.setLong(1, match.id());
                    try (var rows = statement.executeQuery()) {
                        if (rows.next()) throw new IllegalStateException("商品存在业务引用，拒绝清理：" + product.name() + " / " + table);
                    }
                }
            }
            Set<String> events = product.skus().stream().map(s -> EVENT_PREFIX + code(product, s)).collect(Collectors.toSet());
            try (var statement = connection.prepareStatement(
                    "SELECT event_key FROM stock_movement WHERE product_id=? FOR UPDATE")) {
                statement.setLong(1, match.id());
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        if (!events.contains(rows.getString(1)))
                            throw new IllegalStateException("商品已有后续库存变动，拒绝清理：" + product.name());
                    }
                }
            }
            removable.add(match.id());
        }
        for (long id : removable) {
            update(connection, "DELETE FROM stock_movement WHERE product_id=?", id);
            update(connection, "DELETE FROM product_sku WHERE product_id=?", id);
            update(connection, "DELETE FROM product WHERE id=?", id);
        }
        System.out.println("已清理本命令创建的 " + removable.size() + " 件商品；保留原有商品、分类和图片。");
    }

    private static void copyImages(Catalog catalog, Path uploadPath, Path assetPath, List<Path> created)
            throws Exception {
        Path upload = uploadPath.toAbsolutePath().normalize();
        Path assets = assetPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(upload) || !Files.isDirectory(assets))
            throw new IllegalArgumentException("上传目录或素材目录不存在");
        for (ProductSeed product : catalog.products()) {
            Path source = assets.resolve(product.image()).normalize();
            Path target = upload.resolve("products").resolve(product.image()).normalize();
            if (!source.startsWith(assets) || !target.startsWith(upload) || !Files.isRegularFile(source))
                throw new IllegalStateException("商品图片路径无效：" + product.image());
            if (Files.exists(target)) {
                if (!MessageDigest.isEqual(digest(source), digest(target)))
                    throw new IllegalStateException("同名图片内容不同，拒绝覆盖：" + target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
                created.add(target);
            }
        }
    }

    private static byte[] digest(Path path) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
    }

    private static void validate(Catalog catalog) {
        if (catalog.version() != 2 || catalog.products().size() != 40 || catalog.categories().size() != 7)
            throw new IllegalArgumentException("商品目录版本或数量不符合约定");
        Set<String> categoryKeys = new HashSet<>(), keys = new HashSet<>(), codes = new HashSet<>(), aliases = new HashSet<>();
        for (CategorySeed category : catalog.categories()) {
            if (!categoryKeys.add(category.key()) || (category.parent() != null && !categoryKeys.contains(category.parent())))
                throw new IllegalArgumentException("分类关系无效");
            visibleText(category.name());
        }
        for (ProductSeed product : catalog.products()) {
            if (!keys.add(product.key()) || !categoryKeys.contains(product.category())
                    || !product.image().matches("[a-z]+/qivo-[a-z0-9-]+\\.webp")
                    || product.status() < 0 || product.status() > 1 || product.skus().isEmpty()
                    || (!product.aliases().isEmpty() && product.aliases().size() != product.skus().size()))
                throw new IllegalArgumentException("商品清单不合法：" + product.key());
            visibleText(product.name()); visibleText(product.description()); visibleText(product.alt());
            Map<String, Set<String>> allowed = new HashMap<>();
            for (var definition : product.definitions()) {
                visibleText(definition.name());
                Set<String> values = new HashSet<>();
                for (var value : definition.values()) { visibleText(value.name()); values.add(value.id()); }
                if (allowed.put(definition.id(), values) != null || values.size() != definition.values().size())
                    throw new IllegalArgumentException("规格定义重复");
            }
            Set<String> combinations = new HashSet<>();
            for (SkuSeed sku : product.skus()) {
                if (!codes.add(code(product, sku)) || code(product, sku).length() > 64
                        || sku.price().signum() <= 0 || sku.price().scale() > 2 || sku.stock() < 0
                        || sku.status() == null || !combinations.add(specKey(sku))
                        || sku.selections().size() != allowed.size())
                    throw new IllegalArgumentException("规格价格、库存或组合无效");
                Set<String> selected = new HashSet<>();
                for (var selection : sku.selections()) {
                    if (!allowed.getOrDefault(selection.specId(), Set.of()).contains(selection.valueId())
                            || !selected.add(selection.specId())) throw new IllegalArgumentException("规格组合无效");
                }
            }
            if (product.definitions().isEmpty() && product.skus().size() != 1)
                throw new IllegalArgumentException("单规格数量错误");
            for (String alias : product.aliases()) if (!aliases.add(alias)) throw new IllegalArgumentException("既有规格标识重复");
        }
    }

    private static void visibleText(String text) {
        if (text == null || text.isBlank() || text.matches("(?is).*(演示|示例|开发环境|demo).*"))
            throw new IllegalArgumentException("可见文案含有禁用标识或为空");
    }

    private static void validateTarget(Connection connection, String target) throws Exception {
        if (target == null || !target.matches("[a-zA-Z0-9_]+")
                || BLOCKED.contains(target.toLowerCase(Locale.ROOT)) || !target.equals(connection.getCatalog()))
            throw new IllegalArgumentException("目标库名必须与确认值一致，禁止操作 mall 或系统库");
    }

    private static int scalar(Connection connection, String sql, Object... args) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }
    private static void update(Connection connection, String sql, Object... args) throws Exception {
        try (var statement = connection.prepareStatement(sql)) { bind(statement, args); statement.executeUpdate(); }
    }
    private static void bind(PreparedStatement statement, Object... args) throws Exception {
        for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
    }
    private static long generatedId(PreparedStatement statement) throws Exception {
        try (var rows = statement.getGeneratedKeys()) {
            if (!rows.next()) throw new IllegalStateException("数据库未返回新记录编号");
            return rows.getLong(1);
        }
    }
    private static String specKey(SkuSeed sku) {
        TreeMap<String, String> selected = new TreeMap<>();
        for (var selection : sku.selections()) selected.put(selection.specId(), selection.valueId());
        return selected.isEmpty() ? "DEFAULT" : selected.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("|"));
    }
    private static ProductSpecMode mode(ProductSeed product) {
        return product.definitions().isEmpty() ? ProductSpecMode.SINGLE : ProductSpecMode.MULTI;
    }
    private static String code(ProductSeed product, SkuSeed sku) {
        return CODE_PREFIX + product.key().toUpperCase(Locale.ROOT) + "-" + sku.suffix();
    }
    private static String image(String relative) { return IMAGE_PREFIX + relative; }
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少环境变量：" + key);
        return value;
    }

    public record Catalog(int version, List<CategorySeed> categories, List<ProductSeed> products) {}
    public record CategorySeed(String key, String name, String parent, List<String> previousNames, int order, String image) {}
    public record ProductSeed(
            String key, String name, String category, String description, String image, String alt,
            int status, List<SkuSpecificationDefinition> definitions, List<SkuSeed> skus,
            List<String> aliases, String legacyImage) {}
    public record SkuSeed(String suffix, List<SkuSelection> selections, BigDecimal price, int stock, SkuStatus status) {}
    private record ProductMatch(long id, boolean owned) {}
}
