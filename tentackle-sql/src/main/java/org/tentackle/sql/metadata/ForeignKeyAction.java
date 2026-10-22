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

import java.sql.DatabaseMetaData;

/**
 * The referential actions of a foreign key.<br>
 * Maps the {@code importedKey...} codes of {@link DatabaseMetaData#getImportedKeys} to their SQL clauses.
 * <p>
 * The deferrability codes are listed as well, since JDBC reports them via the same constants.
 * However, the deferrability of a foreign key is described by {@link Deferrability}.
 *
 * @author harald
 */
public enum ForeignKeyAction {

  /**
   * When the primary key is updated or deleted, the foreign key (imported key) is changed or deleted accordingly.
   */
  CASCADE(DatabaseMetaData.importedKeyCascade, "CASCADE"),

  /**
   * The primary key may not be updated or deleted if it has been imported by another table as a foreign key.
   */
  RESTRICT(DatabaseMetaData.importedKeyRestrict, "RESTRICT"),

  /**
   * When the primary key is updated or deleted, the foreign key (imported key) is changed to <code>NULL</code>.
   */
  SET_NULL(DatabaseMetaData.importedKeySetNull, "SET NULL"),

  /**
   * If the primary key has been imported, it cannot be updated or deleted.
   */
  NO_ACTION(DatabaseMetaData.importedKeyNoAction, "NO ACTION"),

  /**
   * If the primary key is updated or deleted, the foreign key (imported key) is set to the default value.
   */
  SET_DEFAULT(DatabaseMetaData.importedKeySetDefault, "SET DEFAULT"),

  /**
   * Indicates deferrability. See SQL-92 for a definition.
   */
  INITIALLY_DEFERRED(DatabaseMetaData.importedKeyInitiallyDeferred, "INITIALLY DEFERRED"),

  /**
   * Indicates deferrability. See SQL-92 for a definition.
   */
  INITIALLY_IMMEDIATE(DatabaseMetaData.importedKeyInitiallyImmediate, "INITIALLY IMMEDIATE"),

  /**
   * Indicates deferrability. See SQL-92 for a definition.
   */
  NOT_DEFERRABLE(DatabaseMetaData.importedKeyNotDeferrable, "NOT DEFERRABLE");



  private final int action;     // action number
  private final String sql;     // SQL snippet

  /**
   * Creates an action.
   *
   * @param action the numeric code
   * @param sql the SQL snippet
   */
  ForeignKeyAction(int action, String sql) {
    this.action = action;
    this.sql = sql;
  }

  /**
   * Gets the action code.
   *
   * @return the action code
   */
  public int getAction() {
    return action;
  }

  /**
   * Gets the SQL code.
   *
   * @return the sql
   */
  public String getSql() {
    return sql;
  }

  @Override
  public String toString() {
    return sql;
  }



  /**
   * Creates a foreign key action from an action code.
   *
   * @param action the code
   * @return the foreign key action, null if invalid code
   */
  public static ForeignKeyAction createFromAction(int action) {
    for (ForeignKeyAction fka: ForeignKeyAction.values()) {
      if (fka.action == action) {
        return fka;
      }
    }
    return null;
  }

}
