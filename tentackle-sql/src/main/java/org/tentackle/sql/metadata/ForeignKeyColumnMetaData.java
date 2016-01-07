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
import java.util.Objects;
import org.tentackle.common.BasicStringHelper;
import org.tentackle.sql.BackendException;


/**
 * Meta data for a column of a foreign key.
 *
 * @author harald
 */
public class ForeignKeyColumnMetaData implements Comparable<ForeignKeyColumnMetaData> {

  protected final ForeignKeyMetaData foreignKeyMetaData;    // foreign key column belongs to
  protected int position;                                   // ordinal position within the foreign key
  protected String foreignKeyColumn;                        // the column name of the fk
  protected String primaryKeyColumn;                        // the column name of the pk


  /**
   * Creates a foreign key column.
   *
   * @param foreignKeyMetaData the foreign key column belongs to
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
   * Gets the ordinal position within the index.
   *
   * @return the position
   */
  public int getPosition() {
    return position;
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
   * Gets the referenced column.
   *
   * @return the column
   */
  public String getPrimaryKeyColumn() {
    return primaryKeyColumn;
  }



  /**
   * Sets up the foreign key column from the database meta data result.
   *
   * @param resultSet the column result set
   * @throws SQLException the processing the result set failed
   */
  public void setupForeignKeyColumnFromMetaData(ResultSet resultSet) throws SQLException {

    position = resultSet.getShort("KEY_SEQ");
    foreignKeyColumn = BasicStringHelper.toLower(resultSet.getString("FKCOLUMN_NAME"));
    primaryKeyColumn = BasicStringHelper.toLower(resultSet.getString("PKCOLUMN_NAME"));

    String val = BasicStringHelper.toLower(resultSet.getString("FKTABLE_SCHEM"));
    if (!Objects.equals(val, foreignKeyMetaData.getForeignKeySchema())) {
      throw new BackendException("inconsistent referencing schema in " + foreignKeyMetaData.getTableMetaData().
              getFullTableName() +
              ": " + foreignKeyMetaData.getForeignKeyName() + "." + this + ", found " + val + ", expected " +
              foreignKeyMetaData.getForeignKeySchema());
    }

    val = BasicStringHelper.toLower(resultSet.getString("FKTABLE_NAME"));
    if (!Objects.equals(val, foreignKeyMetaData.getForeignKeyTable())) {
      throw new BackendException("inconsistent referencing table in " + foreignKeyMetaData.getTableMetaData().
              getFullTableName() +
              ": " + foreignKeyMetaData.getForeignKeyName() + "." + this + ", found " + val + ", expected " +
              foreignKeyMetaData.getForeignKeyTable());
    }

    val = BasicStringHelper.toLower(resultSet.getString("PKTABLE_SCHEM"));
    if (!Objects.equals(val, foreignKeyMetaData.getPrimaryKeySchema())) {
      throw new BackendException("inconsistent referenced schema in " + foreignKeyMetaData.getTableMetaData().
              getFullTableName() +
              ": " + foreignKeyMetaData.getForeignKeyName() + "." + this + ", found " + val + ", expected " +
              foreignKeyMetaData.getPrimaryKeySchema());
    }

    val = BasicStringHelper.toLower(resultSet.getString("PKTABLE_NAME"));
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

    validate();
  }

  /**
   * Validates and postprocesses the foreign key column data.
   */
  public void validate() {
  }

  @Override
  public int compareTo(ForeignKeyColumnMetaData o) {
    return position - o.position;
  }

  @Override
  public String toString() {
    return foreignKeyColumn;
  }

}
