/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx.table.edit;

import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.table.DefaultTableColumnConfiguration;
import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers to test the edit handlers without a JavaFX toolkit.<p>
 * The tests must not create any {@code Control}, which is why the column configurations are
 * created directly instead of being added to the table configuration (adding a column
 * configuration eagerly creates the table- and treetable columns).
 */
final class EditTestSupport {

  /** the row object of the tests. */
  static final class Line {

    final String name;

    Line(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }


  /**
   * An edit context backed by a list of rows.
   */
  static class TestEditContext extends AbstractTableEditContext<Line> {

    final DefaultTableConfiguration<Line> configuration = new DefaultTableConfiguration<>(Line.class, "lines");
    final List<TableColumnConfiguration<Line,?>> columns = new ArrayList<>();
    final List<Line> rows = new ArrayList<>();
    TableCellRef<Line> edited;
    TableCellRef<Line> selected;
    int refreshCount;

    /**
     * Creates a context.
     *
     * @param columnNames the names of the columns
     */
    TestEditContext(String... columnNames) {
      for (String columnName : columnNames) {
        columns.add(new DefaultTableColumnConfiguration<Line,Object>(configuration, columnName, columnName));
      }
    }

    /**
     * Adds rows.
     *
     * @param names the names of the rows
     * @return the rows
     */
    Line[] addRows(String... names) {
      Line[] lines = new Line[names.length];
      for (int i = 0; i < names.length; i++) {
        lines[i] = new Line(names[i]);
        rows.add(lines[i]);
      }
      return lines;
    }

    /**
     * Gets a column configuration.
     *
     * @param columnName the name of the column
     * @return the column configuration
     */
    TableColumnConfiguration<Line,?> column(String columnName) {
      for (TableColumnConfiguration<Line,?> column : columns) {
        if (column.getName().equals(columnName)) {
          return column;
        }
      }
      throw new FxRuntimeException("no such column '" + columnName + "'");
    }

    /**
     * Creates an edit.
     *
     * @param row the row
     * @param columnName the name of the column
     * @param trigger the trigger
     * @return the edit
     */
    TableCellEdit<Line,Object> edit(Line row, String columnName, TableEditTrigger trigger) {
      return new TableCellEdit<>(cell(row, columnName), null, "new", trigger);
    }

    @Override
    public TableConfiguration<Line> getConfiguration() {
      return configuration;
    }

    @Override
    public List<TableColumnConfiguration<Line,?>> getColumns() {
      return columns;
    }

    @Override
    public TableCellRef<Line> cell(Line row, String columnName) {
      return new TableCellRef<>(row, column(columnName));
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public Line getRow(int index) {
      return rows.get(index);
    }

    @Override
    public void addRow(Line row) {
      rows.add(row);
    }

    @Override
    public void insertRowAfter(Line sibling, Line row) {
      rows.add(indexOf(sibling) + 1, row);
    }

    @Override
    public void insertRowBefore(Line sibling, Line row) {
      rows.add(indexOf(sibling), row);
    }

    @Override
    public boolean removeRow(Line row) {
      int index = indexOf(row);
      if (index >= 0) {
        rows.remove(index);
        return true;
      }
      return false;
    }

    @Override
    public void edit(TableCellRef<Line> cell) {
      edited = cell;
    }

    @Override
    public void select(TableCellRef<Line> cell) {
      selected = cell;
    }

    @Override
    public void refresh() {
      refreshCount++;
    }
  }


  private EditTestSupport() {
  }

}
