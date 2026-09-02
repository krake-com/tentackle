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
 * The deferrability of a unique or foreign key constraint.
 *
 * @author harald
 */
public enum Deferrability {

  /**
   * The constraint is not deferrable: it is always checked immediately, regardless of
   * {@code SET CONSTRAINTS ... DEFERRED}.
   */
  NOT_DEFERRABLE(DatabaseMetaData.importedKeyNotDeferrable, ""),

  /**
   * The constraint is deferrable and checked immediately by default, unless a transaction
   * explicitly defers it via {@code SET CONSTRAINTS ... DEFERRED}.
   */
  DEFERRABLE_IMMEDIATE(DatabaseMetaData.importedKeyInitiallyImmediate, "DEFERRABLE INITIALLY IMMEDIATE"),

  /**
   * The constraint is deferrable and checked at the end of the transaction by default.
   */
  DEFERRABLE_DEFERRED(DatabaseMetaData.importedKeyInitiallyDeferred, "DEFERRABLE INITIALLY DEFERRED");


  private final int action;    // the JDBC DEFERRABILITY code
  private final String sql;    // the DDL clause, empty string if none


  Deferrability(int action, String sql) {
    this.action = action;
    this.sql = sql;
  }

  /**
   * Gets the JDBC {@code DatabaseMetaData} deferrability code.
   *
   * @return the code, see {@link DatabaseMetaData#getImportedKeys}
   */
  public int getAction() {
    return action;
  }

  /**
   * Gets the DDL clause to append to a {@code CREATE}/{@code ADD CONSTRAINT} statement.
   *
   * @return the clause, empty string if the constraint is not deferrable
   */
  public String getSql() {
    return sql;
  }

  /**
   * Returns whether this deferrability requires the constraint to be declared {@code DEFERRABLE}.
   *
   * @return true if deferrable (immediate or deferred)
   */
  public boolean isDeferrable() {
    return this != NOT_DEFERRABLE;
  }

  /**
   * Creates a deferrability from a JDBC {@code DatabaseMetaData} deferrability code.
   *
   * @param action the code
   * @return the deferrability, null if invalid code
   */
  public static Deferrability createFromAction(int action) {
    for (Deferrability deferrability : values()) {
      if (deferrability.action == action) {
        return deferrability;
      }
    }
    return null;
  }

}
