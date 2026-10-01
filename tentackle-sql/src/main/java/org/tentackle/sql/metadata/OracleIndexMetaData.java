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

package org.tentackle.sql.metadata;

import org.tentackle.common.StringHelper;
import org.tentackle.sql.BackendException;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Oracle index metadata.<br>
 * Oracle implements filtered indexes, function-based indexes and descending indexes
 * all the same way: as an index over a hidden, system-generated virtual column.
 * {@link DatabaseMetaData#getIndexInfo} reports such columns as {@code SYS_NC0000n$}
 * and always returns {@code null} for {@code FILTER_CONDITION}.
 * <p>
 * Without the real expression the migrator would consider the index different from
 * its model counterpart and drop and re-create it on every run. Therefore the
 * expressions are read from oracle's data dictionary view {@code ALL_IND_EXPRESSIONS}
 * and applied by {@link OracleIndexColumnMetaData}.
 *
 * @author harald
 */
public class OracleIndexMetaData extends IndexMetaData {

  /** query to retrieve the index expressions from oracle's data dictionary. */
  private static final String SQL_INDEX_EXPRESSIONS =
      // COLUMN_EXPRESSION is a LONG column and must be the last one in the select list
      "SELECT COLUMN_POSITION, COLUMN_EXPRESSION FROM ALL_IND_EXPRESSIONS WHERE TABLE_NAME=? AND INDEX_NAME=?";

  /** optional index owner restriction. */
  private static final String SQL_INDEX_OWNER = " AND INDEX_OWNER=?";

  private Map<Integer,String> columnExpressions;    // lazily loaded <column position: expression>


  /**
   * Creates an index metadata object.
   *
   * @param tableMetaData the table the index belongs to
   */
  public OracleIndexMetaData(TableMetaData tableMetaData) {
    super(tableMetaData);
  }


  /**
   * Gets the expression of an index column.
   *
   * @param position the ordinal position within the index, starting at 1
   * @return the expression, null if the column is not a virtual column
   */
  public String getColumnExpression(int position) {
    if (columnExpressions == null) {
      columnExpressions = loadColumnExpressions();
    }
    return columnExpressions.get(position);
  }


  /**
   * Loads the index expressions from oracle's data dictionary.
   *
   * @return the expressions, empty map if this is a plain index
   */
  private Map<Integer,String> loadColumnExpressions() {
    Map<Integer,String> expressions = new HashMap<>();
    String schemaName = getTableMetaData().getSchemaName();
    // oracle stores unquoted identifiers in uppercase, but the metadata is kept in lowercase
    String tableName = StringHelper.toUpper(getTableMetaData().getTableName());
    String indexName = StringHelper.toUpper(getIndexName());
    String sql = schemaName == null ? SQL_INDEX_EXPRESSIONS : SQL_INDEX_EXPRESSIONS + SQL_INDEX_OWNER;
    try {
      // the metadata may refer to more than one connection: use the first one that sees the index
      for (DatabaseMetaData metaData: getTableMetaData().getModelMetaData().getMetaData()) {
        try (PreparedStatement stmt = metaData.getConnection().prepareStatement(sql)) {
          stmt.setString(1, tableName);
          stmt.setString(2, indexName);
          if (schemaName != null) {
            stmt.setString(3, StringHelper.toUpper(schemaName));
          }
          try (ResultSet resultSet = stmt.executeQuery()) {
            while (resultSet.next()) {
              int position = resultSet.getInt("COLUMN_POSITION");
              String expression = resultSet.getString("COLUMN_EXPRESSION");
              if (expression != null) {
                expressions.put(position, expression);
              }
            }
          }
        }
        if (!expressions.isEmpty()) {
          break;
        }
      }
    }
    catch (SQLException sx) {
      throw new BackendException("cannot read the column expressions of index " + indexName +
                                 " on table " + tableName, sx);
    }
    return expressions;
  }

}
