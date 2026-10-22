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

package org.tentackle.pdo.test;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.ObjectSequenceId;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityInfo;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Index;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.ScriptRunnerResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


interface DbTestUtilitiesHolder {
  DbTestUtilities INSTANCE = ServiceFactory.createService(DbTestUtilities.class, DbTestUtilities.class);
}


/**
 * Low-level session utility methods for tests.<br>
 * Creates the database schema from the model, used by the test base classes for in-memory databases.
 * <p>
 * A {@link Service} that defaults to itself, so an application may replace it.
 */
@Service(DbTestUtilities.class)    // defaults to self
public class DbTestUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static DbTestUtilities getInstance() {
    return DbTestUtilitiesHolder.INSTANCE;
  }


  /**
   * Creates the utilities.
   */
  public DbTestUtilities() {
    // see -Xlint:missing-explicit-ctor
  }


  /**
   * Creates the SQL script that creates the database schema.<br>
   * Used for in-memory test connections.
   * <p>
   * Loads the default model from all {@code META-INF/MODEL-INDEX.LIST} resources on the classpath.
   * The script creates the object ID sequence (if the backend supports sequences), and for each
   * table-providing entity not marked as provided, its schema (if any and not yet created),
   * table and indexes. The foreign keys come last.
   *
   * @param db the low-level database session
   * @return the SQL script
   * @throws ModelException if the model is inconsistent
   * @see Model#loadFromResources(boolean)
   */
  public String createPopulateScript(Db db) throws ModelException {
    Backend backend = db.getBackend();

    StringBuilder script = new StringBuilder();
    if (backend.isSequenceSupported()) {
      script.append(backend.sqlCreateSequence(ObjectSequenceId.DEFAULT_NAME, 1L, 1L))
            .append(backend.getStatementSeparator()).append('\n');
    }

    Model model = Model.getInstance();    // tests always use the default model
    Set<String> schemas = new HashSet<>();

    for (EntityInfo entityInfo : model.loadFromResources(true)) {
      Entity entity = entityInfo.getEntity();
      if (entity.getTableProvidingEntity() == entity && !entity.getOptions().isProvided()) {
        String schema = entity.getSchemaName();   // schema is null if Model.isSchemaNameMapped()
        if (!StringHelper.isAllWhitespace(schema) && schemas.add(schema)) {
          // schema is new: create it
          script.append(backend.sqlCreateSchema(schema)).append(backend.getStatementSeparator()).append('\n');
        }
        script.append(entity.sqlCreateTable(backend));
        for (Index index : entity.getTableIndexes()) {
          script.append(index.sqlCreateIndex(backend, entity));
        }
      }
    }

    Collection<ForeignKey> foreignKeys = model.getForeignKeys();
    if (!foreignKeys.isEmpty()) {
      for (ForeignKey foreignKey : foreignKeys) {
        script.append(foreignKey.sqlCreateForeignKey(backend));
      }
    }

    return script.toString();
  }


  /**
   * Runs an SQL script.<br>
   * Usually the script created by {@link #createPopulateScript(Db)}.
   *
   * @param db the low-level session
   * @param script the SQL script
   * @return the results, never null
   */
  public List<ScriptRunnerResult> runScript(Db db, String script) {
    return db.runScript("populate", script);
  }

}
