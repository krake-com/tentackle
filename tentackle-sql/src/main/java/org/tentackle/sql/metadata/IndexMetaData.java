/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.sql.metadata;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.tentackle.common.StringHelper;
import org.tentackle.common.Constants;

/**
 * Meta data for an index.
 *
 * @author harald
 */
public class IndexMetaData {

  private final TableMetaData tableMetaData;                // the table the index belongs to
  private final List<IndexColumnMetaData> columns;          // the index columns

  private String indexName;                                 // the index name (without schema)
  private boolean unique;                                   // true if unique index
  private String filterCondition;                           // filter condition


  /**
   * Creates an index meta data object.
   *
   * @param tableMetaData the table the index belongs to
   */
  public IndexMetaData(TableMetaData tableMetaData) {
    this.tableMetaData = tableMetaData;
    this.columns = new ArrayList<>();
  }


  /**
   * Sets up the index from the database meta data result.
   *
   * @param resultSet the column result set
   * @throws SQLException the processing the result set failed
   */
  public void setupIndexFromMetaData(ResultSet resultSet) throws SQLException {
    indexName = StringHelper.toLower(resultSet.getString("INDEX_NAME"));
    // cut schema, if any
    int dotNdx = indexName.indexOf('.');
    if (dotNdx >= 0) {
      indexName = indexName.substring(dotNdx + 1);
    }
    unique = !resultSet.getBoolean("NON_UNIQUE");
    filterCondition = resultSet.getString("FILTER_CONDITION");

    validate();
  }


  /**
   * Adds an index column from the database meta data result.
   *
   * @param resultSet the column result set
   * @throws SQLException the processing the result set failed
   */
  public void addIndexColumnFromMetaData(ResultSet resultSet) throws SQLException {
    IndexColumnMetaData column = tableMetaData.getModelMetaData().getBackend().createIndexColumnMetaData(this);
    column.setupIndexColumnFromMetaData(resultSet);
    columns.add(column);
  }


  /**
   * Gets the table.
   *
   * @return the table meta data
   */
  public TableMetaData getTableMetaData() {
    return tableMetaData;
  }

  /**
   * Gets the columns.
   *
   * @return the columns for this index
   */
  public List<IndexColumnMetaData> getColumns() {
    return columns;
  }

  /**
   * Gets the index name.
   *
   * @return the name in lowercase without schema
   */
  public String getIndexName() {
    return indexName;
  }

  /**
   * Sets the index name.
   *
   * @param indexName the name in lowercase without schema
   */
  public void setIndexName(String indexName) {
    this.indexName = indexName;
  }

  /**
   * Returns whether index is unique.
   *
   * @return true if unique
   */
  public boolean isUnique() {
    return unique;
  }

  /**
   * Sets whether index is unique.
   *
   * @param unique true if unique
   */
  public void setUnique(boolean unique) {
    this.unique = unique;
  }

  /**
   * Gets the filter condition.
   *
   * @return the filter, null if none
   */
  public String getFilterCondition() {
    return filterCondition;
  }

  /**
   * Sets the filter condition.
   *
   * @param filterCondition the filter, null if none
   */
  public void setFilterCondition(String filterCondition) {
    this.filterCondition = filterCondition;
  }

  /**
   * Returns whether this is the primary key for the object id.
   *
   * @return true if object id key
   */
  public boolean isPrimaryIdKey() {
    return unique && columns.size() == 1 && columns.get(0).getColumnName().equals(Constants.CN_ID);
  }


  /**
   * Validates and postprocesses the index data.
   */
  public void validate() {
    if (StringHelper.isAllWhitespace(filterCondition)) {
      filterCondition = null; // translate empty string to null
    }
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (isUnique()) {
      buf.append("UNIQUE ");
    }
    buf.append("INDEX ");
    buf.append(getIndexName());
    buf.append(" ON ");
    buf.append(getTableMetaData().getFullTableName());
    buf.append(" (");
    boolean needComma = false;
    for (IndexColumnMetaData column : getColumns()) {
      if (needComma) {
        buf.append(", ");
      }
      buf.append(column);
      needComma = true;
    }
    buf.append(")");
    if (getFilterCondition() != null) {
      buf.append(" WHERE ").append(getFilterCondition());
    }
    return buf.toString();
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 43 * hash + Objects.hashCode(this.tableMetaData);
    hash = 43 * hash + Objects.hashCode(this.indexName);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final IndexMetaData other = (IndexMetaData) obj;
    if (!Objects.equals(this.tableMetaData, other.tableMetaData)) {
      return false;
    }
    return Objects.equals(this.indexName, other.indexName);
  }

}
