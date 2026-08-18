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

import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The filter of a table or treetable view.<p>
 * Holds a {@link TableColumnFilter} per column. A row passes the table filter if it passes
 * all active column filters, i.e. the column filters are combined by a logical <em>and</em>.
 * <p>
 * The filter is not observable itself, but maintains a {@link #revisionProperty() revision}
 * that is incremented whenever one of the column filters is modified.
 *
 * @param <S> type of the objects contained within the table's or treetable's items list
 * @author harald
 */
public class TableFilter<S> implements Predicate<S> {

  private final TableConfiguration<S> configuration;
  private final Map<TableColumnConfiguration<S,?>, TableColumnFilter<S,?>> columnFilters = new LinkedHashMap<>();
  private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper(this, "revision");


  /**
   * Creates a table filter.
   *
   * @param configuration the table configuration
   */
  public TableFilter(TableConfiguration<S> configuration) {
    this.configuration = configuration;
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    for (TableColumnFilter<S,?> columnFilter : columnFilters.values()) {
      if (columnFilter.isActive()) {
        if (!buf.isEmpty()) {
          buf.append(", ");
        }
        buf.append(columnFilter);
      }
    }
    return buf.toString();
  }


  /**
   * Gets the table configuration.
   *
   * @return the configuration
   */
  public TableConfiguration<S> getConfiguration() {
    return configuration;
  }

  /**
   * Gets the filter of a column.<br>
   * The column filter is created if it does not exist yet.
   *
   * @param <T> the column's value type
   * @param columnConfiguration the column configuration
   * @return the column filter, never null
   */
  @SuppressWarnings("unchecked")
  public <T> TableColumnFilter<S,T> getColumnFilter(TableColumnConfiguration<S,T> columnConfiguration) {
    return (TableColumnFilter<S,T>) columnFilters.computeIfAbsent(
             columnConfiguration, config -> createColumnFilter((TableColumnConfiguration<S,T>) config));
  }

  /**
   * Gets all column filters created so far.
   *
   * @return the column filters
   */
  public Collection<TableColumnFilter<S,?>> getColumnFilters() {
    return columnFilters.values();
  }

  /**
   * Returns whether at least one column filter is active.
   *
   * @return true if the filter restricts the rows
   */
  public boolean isActive() {
    for (TableColumnFilter<S,?> columnFilter : columnFilters.values()) {
      if (columnFilter.isActive()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Clears all column filters.
   */
  public void clear() {
    for (TableColumnFilter<S,?> columnFilter : columnFilters.values()) {
      columnFilter.clear();
    }
  }

  @Override
  public boolean test(S row) {
    return test(row, null);
  }

  /**
   * Tests whether a row passes all active column filters except one.<br>
   * Excluding a column is what the filter popup needs to determine the values still selectable
   * for that very column.
   *
   * @param row the row object, may be null
   * @param excludedFilter the column filter to ignore, null to apply all
   * @return true if the row passes
   */
  public boolean test(S row, TableColumnFilter<S,?> excludedFilter) {
    for (TableColumnFilter<S,?> columnFilter : columnFilters.values()) {
      if (columnFilter != excludedFilter && !columnFilter.test(row)) {
        return false;
      }
    }
    return true;
  }


  /**
   * Gets the current revision.<br>
   * The revision changes whenever one of the column filters is modified.
   *
   * @return the revision
   */
  public int getRevision() {
    return revision.get();
  }

  /**
   * The revision property.
   *
   * @return the property
   * @see #getRevision()
   */
  public ReadOnlyIntegerProperty revisionProperty() {
    return revision.getReadOnlyProperty();
  }

  /**
   * Notifies this filter that one of its column filters has been modified.
   */
  public void fireChanged() {
    revision.set(revision.get() + 1);
  }

  /**
   * Creates the filter for a column.
   *
   * @param <T> the column's value type
   * @param columnConfiguration the column configuration
   * @return the column filter, never null
   */
  protected <T> TableColumnFilter<S,T> createColumnFilter(TableColumnConfiguration<S,T> columnConfiguration) {
    return new TableColumnFilter<>(this, columnConfiguration);
  }

}
