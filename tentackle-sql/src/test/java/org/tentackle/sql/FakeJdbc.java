/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.sql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC test doubles based on dynamic proxies.
 * <p>
 * Only the handful of methods the metadata- and script-processing code actually invokes
 * are implemented; everything else fails loudly. This allows to set up metadata that no real
 * backend would ever produce, such as an inconsistent answer to {@code getTables} and
 * {@code getColumns}, or the interleaved rows of two composite foreign keys.
 */
public final class FakeJdbc {

  /**
   * Provides the rows returned by a {@link DatabaseMetaData} method.
   */
  @FunctionalInterface
  public interface RowSource {

    /**
     * Gets the rows for a metadata request.
     *
     * @param methodName the name of the invoked {@link DatabaseMetaData} method
     * @param args the arguments of the invocation
     * @return the rows, never null
     */
    List<Map<String, Object>> getRows(String methodName, Object[] args);
  }


  /**
   * Creates a result set row.
   *
   * @param columnsAndValues pairs of the column name and its value
   * @return the row
   */
  public static Map<String, Object> row(Object... columnsAndValues) {
    if (columnsAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("column names and values must be given in pairs");
    }
    Map<String, Object> row = new HashMap<>();
    for (int i = 0; i < columnsAndValues.length; i += 2) {
      row.put((String) columnsAndValues[i], columnsAndValues[i + 1]);
    }
    return row;
  }

  /**
   * Creates a result set over the given rows.<br>
   * Requesting a column that is not in the row fails, so that a typo in a fixture
   * does not silently turn into a null value.
   *
   * @param rows the rows
   * @return the result set
   */
  public static ResultSet resultSet(List<Map<String, Object>> rows) {
    return (ResultSet) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                                              new Class<?>[] { ResultSet.class },
                                              new ResultSetHandler(rows));
  }

  /**
   * Creates a database metadata returning the rows provided by the given source.
   *
   * @param rowSource the source of the rows
   * @return the database metadata
   */
  public static DatabaseMetaData databaseMetaData(RowSource rowSource) {
    return (DatabaseMetaData) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                                                     new Class<?>[] { DatabaseMetaData.class },
                                                     new DatabaseMetaDataHandler(rowSource));
  }


  /**
   * Serves the rows of a result set.
   */
  private static final class ResultSetHandler implements InvocationHandler {

    private final List<Map<String, Object>> rows;
    private int index = -1;

    private ResultSetHandler(List<Map<String, Object>> rows) {
      this.rows = rows;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "next" -> ++index < rows.size();
        case "close", "clearWarnings" -> null;
        case "getWarnings" -> null;
        case "getString" -> {
          Object value = value(args[0]);
          yield value == null ? null : value.toString();
        }
        case "getObject" -> value(args[0]);
        case "getInt" -> {
          Object value = value(args[0]);
          yield value == null ? 0 : ((Number) value).intValue();
        }
        case "getShort" -> {
          Object value = value(args[0]);
          yield value == null ? (short) 0 : ((Number) value).shortValue();
        }
        case "getBoolean" -> {
          Object value = value(args[0]);
          yield value != null && (Boolean) value;
        }
        case "toString" -> "fake result set at row " + index;
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> throw new UnsupportedOperationException("fake result set does not implement " + method);
      };
    }

    /**
     * Gets the value of a column of the current row.
     *
     * @param columnName the column name
     * @return the value, null if the column is null
     */
    private Object value(Object columnName) {
      if (index < 0 || index >= rows.size()) {
        throw new IllegalStateException("fake result set is not positioned on a row");
      }
      Map<String, Object> row = rows.get(index);
      if (!row.containsKey(columnName)) {
        throw new IllegalArgumentException("column " + columnName + " missing in fake result set row " + index);
      }
      return row.get(columnName);
    }
  }


  /**
   * Serves the result sets of the database metadata.
   */
  private static final class DatabaseMetaDataHandler implements InvocationHandler {

    private final RowSource rowSource;

    private DatabaseMetaDataHandler(RowSource rowSource) {
      this.rowSource = rowSource;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "getTables", "getColumns", "getIndexInfo", "getImportedKeys" ->
            resultSet(rowSource.getRows(method.getName(), args));
        case "toString" -> "fake database meta data";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> throw new UnsupportedOperationException("fake database meta data does not implement " + method);
      };
    }
  }


  /**
   * don't instantiate.
   */
  private FakeJdbc() {
  }

}
