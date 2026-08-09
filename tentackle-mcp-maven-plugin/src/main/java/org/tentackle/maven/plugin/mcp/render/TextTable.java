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

package org.tentackle.maven.plugin.mcp.render;

import java.util.ArrayList;
import java.util.List;

/**
 * A column-aligned text table.
 * <p>
 * Aligned columns cost a few spaces and save the reader a lot of parsing, which is just as true for a model as
 * for a human.
 *
 * @author harald
 */
public class TextTable {

  /** The gap between two columns. */
  public static final String COLUMN_GAP = "  ";

  private final List<String> header;
  private final List<List<String>> rows;

  /**
   * Creates a table.
   *
   * @param header the column headers
   */
  public TextTable(String... header) {
    this.header = List.of(header);
    this.rows = new ArrayList<>();
  }

  /**
   * Adds a row.
   * <p>
   * Null cells are rendered as an empty string. Missing cells are padded, extra cells are an error.
   *
   * @param cells the cells
   * @return this table
   */
  public TextTable add(Object... cells) {
    if (cells.length > header.size()) {
      throw new IllegalArgumentException("row has " + cells.length + " cells but the table has " +
                                         header.size() + " columns");
    }
    List<String> row = new ArrayList<>(header.size());
    for (int i = 0; i < header.size(); i++) {
      Object cell = i < cells.length ? cells[i] : null;
      row.add(cell == null ? "" : cell.toString());
    }
    rows.add(row);
    return this;
  }

  /**
   * Returns whether the table holds no rows.
   *
   * @return true if empty
   */
  public boolean isEmpty() {
    return rows.isEmpty();
  }

  /**
   * Gets the number of rows.
   *
   * @return the row count
   */
  public int size() {
    return rows.size();
  }

  /**
   * Renders the table.
   * <p>
   * Trailing whitespace is trimmed from every line: a wide last column would otherwise pad every row to the
   * width of its longest value for no gain.
   *
   * @return the rendered table; an empty string if there are no rows
   */
  @Override
  public String toString() {
    if (rows.isEmpty()) {
      return "";
    }

    int[] widths = new int[header.size()];
    for (int i = 0; i < header.size(); i++) {
      widths[i] = header.get(i).length();
    }
    for (List<String> row : rows) {
      for (int i = 0; i < header.size(); i++) {
        widths[i] = Math.max(widths[i], row.get(i).length());
      }
    }

    StringBuilder buf = new StringBuilder(256);
    appendRow(buf, header, widths);
    List<String> separator = new ArrayList<>(header.size());
    for (int width : widths) {
      separator.add("-".repeat(width));
    }
    appendRow(buf, separator, widths);
    for (List<String> row : rows) {
      appendRow(buf, row, widths);
    }
    return buf.toString();
  }

  private void appendRow(StringBuilder buf, List<String> cells, int[] widths) {
    StringBuilder line = new StringBuilder(80);
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        line.append(COLUMN_GAP);
      }
      String cell = cells.get(i);
      line.append(cell);
      if (i < cells.size() - 1) {
        line.repeat(" ", widths[i] - cell.length());
      }
    }
    buf.append(line.toString().stripTrailing()).append('\n');
  }

}
