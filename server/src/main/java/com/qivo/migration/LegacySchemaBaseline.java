package com.qivo.migration;

import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;

/** 独立命令：只读校验默认执行；明确确认库名后才写 Flyway 版本 1 基线。 */
public final class LegacySchemaBaseline {
    private LegacySchemaBaseline() {}

    public static void validate(Connection c) throws Exception {
        if (!c.getMetaData().getDatabaseProductName().equals("MySQL"))
            throw new IllegalStateException("基线校验仅支持 MySQL");
        String sql;
        try (var input =
                LegacySchemaBaseline.class.getResourceAsStream(
                        "/db/migration/V1__legacy_schema.sql")) {
            if (input == null) throw new IllegalStateException("缺少 V1 结构定义");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var tables =
                Pattern.compile("CREATE TABLE `([^`]+)` \\((.*?)\\) ENGINE", Pattern.DOTALL)
                        .matcher(sql);
        Set<String> expectedTables = new HashSet<>();
        while (tables.find()) {
            String table = tables.group(1), body = tables.group(2);
            expectedTables.add(table);
            Map<String, Column> actual = new HashMap<>();
            try (var rows = c.getMetaData().getColumns(c.getCatalog(), null, table, null)) {
                while (rows.next())
                    actual.put(
                            rows.getString("COLUMN_NAME"),
                            new Column(
                                    rows.getString("TYPE_NAME"),
                                    rows.getInt("COLUMN_SIZE"),
                                    rows.getInt("DECIMAL_DIGITS"),
                                    rows.getInt("NULLABLE"),
                                    rows.getString("IS_AUTOINCREMENT")));
            }
            Set<String> expectedColumns = new HashSet<>();
            var columns =
                    Pattern.compile(
                                    "(?m)^\\s*`([^`]+)`\\s+(\\w+)(?:\\((\\d+)(?:,(\\d+))?\\))?([^\\n]*)")
                            .matcher(body);
            while (columns.find()) {
                String name = columns.group(1), type = columns.group(2);
                expectedColumns.add(name);
                Column col = actual.get(name);
                if (col == null || !col.type().equalsIgnoreCase(type))
                    throw new IllegalStateException("字段类型不匹配：" + table + "." + name);
                if (columns.group(3) != null
                        && (type.equalsIgnoreCase("VARCHAR") || type.equalsIgnoreCase("DECIMAL"))) {
                    if (col.size() != Integer.parseInt(columns.group(3)))
                        throw new IllegalStateException("字段长度不匹配：" + table + "." + name);
                }
                if (columns.group(4) != null && col.scale() != Integer.parseInt(columns.group(4)))
                    throw new IllegalStateException("精度不匹配：" + table + "." + name);
                boolean notNull = columns.group(5).contains("NOT NULL");
                if (notNull != (col.nullable() == DatabaseMetaData.columnNoNulls))
                    throw new IllegalStateException("空值约束不匹配：" + table + "." + name);
                if (columns.group(5).contains("AUTO_INCREMENT")
                        && !"YES".equals(col.autoIncrement()))
                    throw new IllegalStateException("自增约束不匹配：" + table + "." + name);
            }
            if (!actual.keySet().equals(expectedColumns))
                throw new IllegalStateException("字段集合不匹配：" + table);
            List<String> primary = new ArrayList<>();
            try (var rows = c.getMetaData().getPrimaryKeys(c.getCatalog(), null, table)) {
                while (rows.next()) primary.add(rows.getString("COLUMN_NAME"));
            }
            if (!primary.equals(List.of("id"))) throw new IllegalStateException("主键不匹配：" + table);
            Map<String, List<String>> indexes = new HashMap<>();
            Set<String> unique = new HashSet<>();
            try (var rows =
                    c.getMetaData().getIndexInfo(c.getCatalog(), null, table, false, false)) {
                while (rows.next()) {
                    String index = rows.getString("INDEX_NAME"),
                            column = rows.getString("COLUMN_NAME");
                    if (index != null && column != null) {
                        indexes.computeIfAbsent(index, k -> new ArrayList<>()).add(column);
                        if (!rows.getBoolean("NON_UNIQUE")) unique.add(index);
                    }
                }
            }
            var definitions =
                    Pattern.compile("(UNIQUE\\s+)?KEY `([^`]+)` \\(([^)]+)\\)").matcher(body);
            while (definitions.find()) {
                String name = definitions.group(2);
                List<String> expected =
                        Arrays.stream(definitions.group(3).split(","))
                                .map(x -> x.trim().replace("`", ""))
                                .toList();
                if (!expected.equals(indexes.get(name))
                        || (definitions.group(1) != null && !unique.contains(name)))
                    throw new IllegalStateException("索引不匹配：" + table + "." + name);
            }
        }
        Set<String> actualTables = new HashSet<>();
        try (var rows =
                c.getMetaData().getTables(c.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (rows.next()) actualTables.add(rows.getString("TABLE_NAME"));
        }
        if (!actualTables.equals(expectedTables))
            throw new IllegalStateException("库中表集合与 V1 不一致，拒绝建立基线");
    }

    private record Column(String type, int size, int scale, int nullable, String autoIncrement) {}

    public static void main(String[] args) throws Exception {
        String url = required("QIVO_BASELINE_URL"),
                user = required("DB_USERNAME"),
                password = required("DB_PASSWORD");
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            validate(c);
            if (args.length == 1 && "--baseline".equals(args[0])) {
                if (!c.getCatalog().equals(System.getenv("QIVO_BASELINE_CONFIRM")))
                    throw new IllegalStateException("必须通过 QIVO_BASELINE_CONFIRM 确认当前库名，并事先完成备份");
                Flyway.configure()
                        .dataSource(url, user, password)
                        .baselineVersion("1")
                        .cleanDisabled(true)
                        .load()
                        .baseline();
                System.out.println("结构校验通过，已建立版本 1 基线；未执行后续迁移。");
            } else if (args.length == 0) System.out.println("结构校验通过；只读检查，未写入基线。");
            else throw new IllegalArgumentException("仅支持无参数只读检查或 --baseline");
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少环境变量：" + key);
        return value;
    }
}
