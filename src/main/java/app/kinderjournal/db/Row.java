package app.kinderjournal.db;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * A thin, forgiving wrapper around a {@link ResultSet}.
 *
 * <p>Column lookups are by name and cached, missing columns return sensible
 * defaults, and {@link SQLException} is converted to {@link DataException}, so
 * mapping code reads as a list of assignments instead of try/catch blocks.
 */
public final class Row {

    private final ResultSet rs;
    private final Map<String, Integer> columns = new HashMap<>();

    public Row(ResultSet rs) throws SQLException {
        this.rs = rs;
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            if (label != null) {
                columns.putIfAbsent(label.toLowerCase(), i);
            }
        }
    }

    private boolean has(String column) {
        return columns.containsKey(column.toLowerCase());
    }

    public long getLong(String column) {
        if (!has(column)) {
            return 0L;
        }
        try {
            long value = rs.getLong(columns.get(column.toLowerCase()));
            return rs.wasNull() ? 0L : value;
        } catch (SQLException ex) {
            throw new DataException("Reading column '" + column + "'", ex);
        }
    }

    public int getInt(String column) {
        return (int) getLong(column);
    }

    public boolean getBoolean(String column) {
        if (!has(column)) {
            return false;
        }
        try {
            int index = columns.get(column.toLowerCase());
            Object raw = rs.getObject(index);
            if (raw == null) {
                return false;
            }
            if (raw instanceof Boolean b) {
                return b;
            }
            if (raw instanceof Number n) {
                return n.intValue() != 0;
            }
            String text = raw.toString().trim();
            return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
        } catch (SQLException ex) {
            throw new DataException("Reading column '" + column + "'", ex);
        }
    }

    /** Nullable text: returns null (not "") when the column holds SQL NULL. */
    public String getStringOrNull(String column) {
        if (!has(column)) {
            return null;
        }
        try {
            return rs.getString(columns.get(column.toLowerCase()));
        } catch (SQLException ex) {
            throw new DataException("Reading column '" + column + "'", ex);
        }
    }

    /** Text with SQL NULL mapped to the empty string. */
    public String getString(String column) {
        String value = getStringOrNull(column);
        return value == null ? "" : value;
    }
}
