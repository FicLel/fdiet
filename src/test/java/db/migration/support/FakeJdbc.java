package db.migration.support;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * A connection that answers each select with fixed rows, picked by a fragment of
 * its SQL, so the JDBC loaders of {@code support/} can be tested without a
 * database. Only the calls those loaders make are answered.
 */
final class FakeJdbc {

    private FakeJdbc() {
    }

    /** {@code tables}: a fragment the SQL contains (its FROM table) → the rows it answers. */
    static Connection connection(Map<String, List<Map<String, Object>>> tables) {
        Statement statement = proxy(Statement.class, (method, args) -> switch (method) {
            case "executeQuery" -> resultSet(rowsFor(tables, (String) args[0]));
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method);
        });
        return proxy(Connection.class, (method, args) -> switch (method) {
            case "createStatement" -> statement;
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static List<Map<String, Object>> rowsFor(Map<String, List<Map<String, Object>>> tables, String sql) {
        return tables.entrySet().stream()
                .filter(table -> sql.contains(table.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No rows for " + sql));
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] at = {-1};
        boolean[] wasNull = {false};
        return proxy(ResultSet.class, (method, args) -> {
            if (method.equals("next")) {
                return ++at[0] < rows.size();
            }
            if (method.equals("close")) {
                return null;
            }
            if (method.equals("wasNull")) {
                return wasNull[0];
            }
            Object value = rows.get(at[0]).get((String) args[0]);
            wasNull[0] = value == null;
            return switch (method) {
                case "getString" -> value == null ? null : value.toString();
                case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                case "getBoolean" -> value != null && (Boolean) value;
                case "getBigDecimal" -> value == null ? null : new BigDecimal(value.toString());
                default -> throw new UnsupportedOperationException(method);
            };
        });
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(String method, Object[] args) throws Exception;
    }

    private static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[]{type},
                (self, method, args) -> handler.handle(method.getName(), args)));
    }
}
