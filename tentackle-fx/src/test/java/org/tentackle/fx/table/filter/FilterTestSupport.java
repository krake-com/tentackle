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

package org.tentackle.fx.table.filter;

import org.tentackle.fx.table.DefaultTableColumnConfiguration;
import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.fx.table.TableColumnConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Helpers to build column filters without a JavaFX toolkit.<p>
 * The tests must not create any {@code Control}, which is why the column configurations are
 * created directly instead of being added to the table configuration (adding a column
 * configuration eagerly creates the table- and treetable columns).
 */
final class FilterTestSupport {

  /** the row object of the tests. */
  record Row(String city, Integer amount, Boolean paid) { }


  /**
   * A column filter reading its value from a function instead of a binding.
   *
   * @param <T> the column's value type
   */
  static class TestColumnFilter<T> extends TableColumnFilter<Row,T> {

    private final Function<Row,T> valueFunction;

    TestColumnFilter(TableFilter<Row> tableFilter, TableColumnConfiguration<Row,T> columnConfiguration,
                     Function<Row,T> valueFunction) {
      super(tableFilter, columnConfiguration);
      this.valueFunction = valueFunction;
    }

    @Override
    protected T getValue(Row row) {
      return row == null ? null : valueFunction.apply(row);
    }
  }


  /**
   * A table filter creating column filters that read their values from a function.
   */
  static class TestTableFilter extends TableFilter<Row> {

    private final Map<String, Function<Row,?>> valueFunctions = new HashMap<>();

    TestTableFilter(DefaultTableConfiguration<Row> configuration) {
      super(configuration);
    }

    /**
     * Registers the value function of a column.
     *
     * @param name the column name
     * @param valueFunction the function delivering the column's value for a row
     */
    void register(String name, Function<Row,?> valueFunction) {
      valueFunctions.put(name, valueFunction);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> TableColumnFilter<Row,T> createColumnFilter(TableColumnConfiguration<Row,T> columnConfiguration) {
      Function<Row,?> valueFunction = valueFunctions.get(columnConfiguration.getName());
      if (valueFunction == null) {
        return super.createColumnFilter(columnConfiguration);
      }
      return new TestColumnFilter<>(this, columnConfiguration, (Function<Row,T>) valueFunction);
    }
  }


  private FilterTestSupport() {
  }


  /**
   * Creates a table configuration.
   *
   * @return the configuration
   */
  static DefaultTableConfiguration<Row> createTableConfiguration() {
    return new DefaultTableConfiguration<>(Row.class, "rows");
  }

  /**
   * Creates a column configuration.
   *
   * @param <T> the column's value type
   * @param configuration the table configuration
   * @param name the column name
   * @param type the column type
   * @return the column configuration
   */
  static <T> DefaultTableColumnConfiguration<Row,T> createColumnConfiguration(
          DefaultTableConfiguration<Row> configuration, String name, Class<T> type) {
    DefaultTableColumnConfiguration<Row,T> columnConfiguration =
            new DefaultTableColumnConfiguration<>(configuration, name, name);
    columnConfiguration.setType(type);
    return columnConfiguration;
  }

  /**
   * Creates a column filter for a column that is not bound.
   *
   * @param <T> the column's value type
   * @param name the column name
   * @param type the column type
   * @return the column filter
   */
  static <T> TableColumnFilter<Row,T> createColumnFilter(String name, Class<T> type) {
    DefaultTableConfiguration<Row> configuration = createTableConfiguration();
    TableFilter<Row> filter = new TableFilter<>(configuration);
    return filter.getColumnFilter(createColumnConfiguration(configuration, name, type));
  }

}
