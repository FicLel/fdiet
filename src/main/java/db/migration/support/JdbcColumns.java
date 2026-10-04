package db.migration.support;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Reading nullable columns in a Java migration, where no JPA mapping exists yet. */
public final class JdbcColumns {

    private JdbcColumns() {
    }

    /** A nullable BIGINT column, read without turning NULL into 0. */
    public static Long longOrNull(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    /** A nullable enum column, stored by name. */
    public static <E extends Enum<E>> E enumOf(Class<E> type, String name) {
        return name == null ? null : Enum.valueOf(type, name);
    }
}
