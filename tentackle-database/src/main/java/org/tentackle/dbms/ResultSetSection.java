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
 */

package org.tentackle.dbms;

import org.tentackle.session.PersistenceException;

import java.util.HashSet;
import java.util.Set;

/**
 * A section of columns within the result set.
 * <p>
 * Sections allow automatic retrieval of columns in the order of their configuration.
 *
 * @author harald
 */
public class ResultSetSection {

  private final ResultSetWrapper rs;          // the result set
  private final Object key;                   // the key unique with all sections of the resultset
  private Set<String> configuredNames;        // the configured column names, null if nothing configured so far
  private int[] configuredOffsets;            // offsets of configured columns order by retrieval via application
  private int columnIndex;                    // next column index during automatic retrieval

  /**
   * Creates a section.
   *
   * @param rs the result set
   * @param key the key unique with all sections of the resultset (usually a class variable)
   */
  public ResultSetSection(ResultSetWrapper rs, Object key) {
    this.rs = rs;
    this.key = key;
  }


  /**
   * Gets the key of this section.
   *
   * @return the section key
   */
  public Object getKey() {
    return key;
  }


  /**
   * Configures a column for automatic retrieval.
   *
   * @param name the column name
   */
  public void configureColumn(String name) {
    if (configuredNames == null) {
      configuredNames = new HashSet<>();
      configuredOffsets = new int[rs.getColumnCount()];
    }
    int ndx = configuredNames.size();
    if (ndx >= configuredOffsets.length) {
      throw new PersistenceException(rs.getSession(),
              "more columns configured than in result set (" + configuredOffsets.length + ")");
    }
    configuredOffsets[ndx] = rs.findColumn(name);
    if (!configuredNames.add(name)) {
      throw new PersistenceException(rs.getSession(), "column '" + name + "' already configured");
    }
  }


  /**
   * Clears the column index.
   */
  public void resetColumnIndex() {
    columnIndex = 0;
  }


  /**
   * Gets the next column index.
   *
   * @return the next column index
   */
  public int nextColumnIndex() {
    if (configuredOffsets == null) {
      throw new PersistenceException(rs.getSession(), "no columns configured for automatic indexed retrieval");
    }
    if (columnIndex >= configuredNames.size()) {
      throw new PersistenceException(rs.getSession(), "more columns retrieved than configured (" + configuredNames.size() + ")");
    }
    return configuredOffsets[columnIndex++];
  }


}
