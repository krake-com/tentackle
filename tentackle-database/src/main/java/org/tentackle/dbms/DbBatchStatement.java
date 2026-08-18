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

package org.tentackle.dbms;

import org.tentackle.session.PersistenceException;

import java.util.ArrayList;
import java.util.List;

/**
 * Statement and its data used in batched transactions.
 */
@SuppressWarnings("rawtypes")   // due to AbstractDbObject<P>
public class DbBatchStatement {

  /** the prepared statement. */
  private final PreparedStatementWrapper statement;

  /** {@link DbModificationType#INSERT}, {@link DbModificationType#UPDATE} or {@link DbModificationType#DELETE}. */
  private final ModificationType modType;

  /** the persisted objects so far. */
  private final List<AbstractDbObject> objects;


  /**
   * Creates a batched statement.
   *
   * @param statement the prepared statement
   * @param modType the statement type
   */
  public DbBatchStatement(PreparedStatementWrapper statement, ModificationType modType) {
    this.statement = statement;
    this.modType = modType;
    objects = new ArrayList<>();
  }

  /**
   * Gets the statement.
   *
   * @return the prepared statement
   */
  public PreparedStatementWrapper getStatement() {
    return statement;
  }

  /**
   * Gets the statement type.<br>
   * One of {@link DbModificationType#INSERT}, {@link DbModificationType#UPDATE} or {@link DbModificationType#DELETE}.
   *
   * @return the type
   */
  public ModificationType getModType() {
    return modType;
  }

  /**
   * Adds a persistence object to the batch.
   *
   * @param object the PDO
   */
  public void add(AbstractDbObject object) {
    objects.add(object);
  }

  /**
   * Executes the statement.
   *
   * @param finish true if this is the last invocation for this transaction
   */
  public void execute(boolean finish) {
    if (statement.isExecutionPending()) {
      if (statement.getBatchCount() > 0) {
        statement.addBatch();   // add the last parameter set to the batch if not only one set
      }
      if (statement.getBatchCount() > 0 && statement.getBatchCount() == objects.size()) {
        int[] counts = statement.executeBatch(finish);
        if (modType != DbModificationType.INSERT) {  // inserts fail with an exception -> no need to check the count
          int ndx = 0;
          for (int count : counts) {
            if (count != 1) {
              objects.get(ndx).assertThisRowAffected(count);    // -> exception!
            }
            ndx++;
          }
        }
      }
      else if (objects.size() == 1) {    // only one object -> execute without batch
        objects.get(0).assertThisRowAffected(statement.executeUpdate(null));
      }
      else {
        throw new PersistenceException(statement.getSession(), "unexpected number of batched objects: " + objects.size() +
                                                               ", expected: " + statement.getBatchCount());
      }
      objects.clear();
    }
  }

}
