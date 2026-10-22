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

package org.tentackle.model.migrate;

import org.tentackle.model.ForeignKey;
import org.tentackle.model.ModelException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.metadata.ForeignKeyMetaData;

/**
 * Handles the migration of foreign keys.
 *
 * @author harald
 */
public class ForeignKeyMigrator {

  private final Backend backend;                          // the backend
  private final ForeignKey foreignKey;                    // the foreign key to migrate, null if no such fk in model
  private final ForeignKeyMetaData foreignKeyMetaData;    // the foreign key metadata, null if the key does not exist in the database


  /**
   * Creates a migrator.
   *
   * @param backend the backend
   * @param foreignKey the foreign key to migrate, null if no such fk in model
   * @param foreignKeyMetaData the foreign key metadata, null if the key does not exist in the database
   */
  public ForeignKeyMigrator(Backend backend, ForeignKey foreignKey, ForeignKeyMetaData foreignKeyMetaData) {
    this.backend = backend;
    this.foreignKey = foreignKey;
    this.foreignKeyMetaData = foreignKeyMetaData;
  }

  /**
   * Gets the backend.
   *
   * @return the backend
   */
  public Backend getBackend() {
    return backend;
  }

  /**
   * Gets the foreign key to migrate.
   *
   * @return the foreign key, null if no such fk in model
   */
  public ForeignKey getForeignKey() {
    return foreignKey;
  }

  /**
   * Gets the foreign key metadata.
   *
   * @return the metadata, null if no such fk in database
   */
  public ForeignKeyMetaData getForeignKeyMetaData() {
    return foreignKeyMetaData;
  }


  /**
   * Migrates the foreign key.
   *
   * @return the SQL code, empty string if nothing to migrate
   * @throws ModelException if migration failed
   */
  public String migrate() throws ModelException {
    StringBuilder buf = new StringBuilder();

    if (foreignKey == null) {
      // drop existing key
      buf.append(backend.sqlDropForeignKey(foreignKeyMetaData.getFullForeignKeyTableName(), foreignKeyMetaData.getForeignKeyName()));
    }
    else if (foreignKeyMetaData == null) {
      // create missing key
      buf.append(foreignKey.sqlCreateForeignKey(backend));
    }
    else  {
      // callers never pass both: a changed key is migrated as drop plus create
      throw new ModelException("cannot migrate existing foreign key");
    }

    return buf.toString();
  }


}
