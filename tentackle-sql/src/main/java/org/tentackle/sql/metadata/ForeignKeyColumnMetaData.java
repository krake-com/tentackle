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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;


/**
 * Metadata for a column of a foreign key.
 *
 * @author harald
 */
public class ForeignKeyColumnMetaData implements Comparable<ForeignKeyColumnMetaData> {

  private final ForeignKeyMetaData foreignKeyMetaData;    // foreign key column belongs to
  private int position;                                   // ordinal position within the foreign key
  private String foreignKeyColumn;                        // the column name of the fk
  private String primaryKeyColumn;                        // the column name of the pk


  /**
   * Creates a foreign key column.
   *
   * @param foreignKeyMetaData the foreign key this column belongs to
   */
  public ForeignKeyColumnMetaData(ForeignKeyMetaData foreignKeyMetaData) {
    this.foreignKeyMetaData = foreignKeyMetaData;
  }

  /**
   * Gets the foreign key.
   *
   * @return the foreign key this column belongs to
   */
  public ForeignKeyMetaData getForeignKeyMetaData() {
    return foreignKeyMetaData;
  }

  /**
   * Gets the ordinal position within the foreign key.
   *
   * @return the position
   */
  public int getPosition() {
    return position;
  }

  /**
   * Sets the ordinal position within the foreign key.
   *
   * @param position the position
   */
  public void setPosition(int position) {
    this.position = position;
  }

  /**
   * Gets the referencing column.
   *
   * @return the column name
   */
  public String getForeignKeyColumn() {
    return foreignKeyColumn;
  }

  /**
   * Sets the referencing column.
   *
   * @param foreignKeyColumn the column name
   */
  public void setForeignKeyColumn(String foreignKeyColumn) {
    this.foreignKeyColumn = foreignKeyColumn;
  }

  /**
   * Gets the primary key column.
   *
   * @return the column name
   */
  public String getPrimaryKeyColumn() {
    return primaryKeyColumn;
  }

  /**
   * Sets the primary key column.
   *
   * @param primaryKeyColumn the column name
   */
  public void setPrimaryKeyColumn(String primaryKeyColumn) {
    this.primaryKeyColumn = primaryKeyColumn;
  }

  /**
   * Sets up the foreign key column from the database metadata result.
   *
   * @param resultSet the imported keys result set
   * @throws SQLException if processing the result set failed
   * @throws BackendException if the foreign key is inconsistent with the columns processed so far
   */
  public void setupForeignKeyColumnFromMetaData(ResultSet resultSet) throws SQLException {


    String defaultSchema = getForeignKeyMetaData().getTableMetaData().getModelMetaData().getBackend().getDefaultSchema();

    position = resultSet.getShort("KEY_SEQ");
    foreignKeyColumn = StringHelper.toLower(resultSet.getString("FKCOLUMN_NAME"));
    primaryKeyColumn = StringHelper.toLower(resultSet.getString("PKCOLUMN_NAME"));

    String val = StringHelper.toLower(resultSet.getString("FKTABLE_SCHEM"));
    if (defaultSchema != null && defaultSchema.equals(val)) {
      val = null;
    }
    if (!Objects.equals(val, foreignKeyMetaData.getForeignKeySchema())) {
      throw new BackendException("inconsistent referencing schema in " + foreignKeyMetaData.getTableMetaData().
              getFullTableName() +
              ": " + foreignKeyMetaData.getForeignKeyName() + "." + this + ", found " + val + ", expected " +
              foreignKeyMetaData.getForeignKeySchema());
    }

    val = StringHelper.toLower(resultSet.getString("FKTABLE_NAME"));
    if (!Objects.equals(val, foreignKeyMetaData.getForeignKeyTable())) {
      throw new BackendException("inconsistent referencing table in " + foreignKeyMetaData.getTableMetaData().
              getFullTableName() +
              ": " + foreignKeyMetaData.getForeignKeyName() + "." + this + ", found " + val + ", expected " +
              foreignKeyMetaData.getForeignKeyTable());
    }

    val = StringHelper.toLower(resultSet.getString("PKTABLE_SCHEM"));
    if (defaultSchema != null && defaultSchema.equals(val)) {
      val = null;
    }
    if (!Objects.equals(val, foreignKeyMetaData.getPrimaryKeySchema())) {
      throw new BackendException("inconsistent referenced schema in " + foreignKeyMetaData.getTableMetaData().
              getFullTableName() +
              ": " + foreignKeyMetaData.getForeignKeyName() + "." + this + ", found " + val + ", expected " +
              foreignKeyMetaData.getPrimaryKeySchema());
    }

    val = StringHelper.toLower(resultSet.getString("PKTABLE_NAME"));
    if (!Objects.equals(val, foreignKeyMetaData.getPrimaryKeyTable())) {
      throw new BackendException("inconsistent referenced table in " + foreignKeyMetaData.getTableMetaData().
              getFullTableName() +
              ": " + foreignKeyMetaData.getForeignKeyName() + "." + this + ", found " + val + ", expected " +
              foreignKeyMetaData.getPrimaryKeyTable());
    }

    ForeignKeyAction action = ForeignKeyAction.createFromAction(resultSet.getShort("UPDATE_RULE"));
    if (!Objects.equals(action, foreignKeyMetaData.getUpdateRule())) {
      throw new BackendException("inconsistent update rule in " + foreignKeyMetaData.getTableMetaData().
              getFullTableName() +
              ": " + foreignKeyMetaData.getForeignKeyName() + "." + this + ", found " + action + ", expected " +
              foreignKeyMetaData.getUpdateRule());
    }

    action = ForeignKeyAction.createFromAction(resultSet.getShort("DELETE_RULE"));
    if (!Objects.equals(action, foreignKeyMetaData.getDeleteRule())) {
      throw new BackendException("inconsistent delete rule in " + foreignKeyMetaData.getTableMetaData().
              getFullTableName() +
              ": " + foreignKeyMetaData.getForeignKeyName() + "." + this + ", found " + action + ", expected " +
              foreignKeyMetaData.getDeleteRule());
    }

//    validate();   // nothing to validate so far
  }

  @Override
  public int compareTo(ForeignKeyColumnMetaData o) {
    return Integer.compare(position, o.position);
  }

  @Override
  public String toString() {
    return foreignKeyColumn;
  }

}
