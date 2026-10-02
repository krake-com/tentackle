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

import org.tentackle.log.Logger;
import org.tentackle.session.PersistenceException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maintains batches of prepared statements.
 * <p>
 * If batching is enabled for a session, transactions will run in batched mode. In this mode, well-defined standard prepared
 * statements are collected and executed later as a JDBC batch, which will gain some performance.
 * <p>
 * Batching is only performed within the same type of root entities because the execution order is
 * important to ensure referential integrity. Furthermore, the batch is flushed on every savepoint,
 * whenever the same object is persisted again, a non-batchable statement is executed or the transaction committed.
 * <p>
 * Batching is enabled via the backend property <code>batchsize=...</code>, if the value is &gt; 1.
 * However, the batchsize can be set for each session programmatically as well. If certain transactions must be
 * treated individually, the application can provide its own {@link org.tentackle.session.SessionFactory} and
 * override {@link Db#createTransaction(String, boolean)} as well as {@link DbTransaction#createBatch()}.
 * <p>
 * IMPORTANT: batching is not enabled by default because it has its drawbacks, especially when it comes to
 * exceptions and error diagnosis. Furthermore, the performance gains depend on a lot of parameters,
 * for example, the type of database backend, how indexes are affected, etc...
 * Some JDBC drivers also need special connection parameters to take full advantage of batching.
 * <p>
 * Use with great care in production!
 */
@SuppressWarnings("rawtypes")   // due to AbstractDbObject<P>
public class DbBatch {

  private static final Logger LOGGER = Logger.get(DbBatch.class);


  private final Map<StatementKey, DbBatchStatement> statements;     // statement:<list of objects>
  private final Set<AbstractDbObject> objects;                      // all objects so far
  private final DbTransaction transaction;                          // the current transaction
  private final int batchSize;                                      // session's batchsize
  private int lastRootClassId;                                      // last root class ID


  /**
   * Creates a batch for prepared statements.
   *
   * @param transaction the current transaction
   * @param batchSize the batch size, &le; 1 if no batching
   */
  public DbBatch(DbTransaction transaction, int batchSize) {
    this.transaction = transaction;
    this.batchSize = batchSize;

    statements = new LinkedHashMap<>();                     // linked map to keep statements in the order they were added
    objects = new HashSet<>();                              // to force flush as soon as the object is affected more than once
  }

  /**
   * Gets the transaction this batch belongs to.
   *
   * @return the transaction
   */
  public DbTransaction getTransaction() {
    return transaction;
  }

  /**
   * Gets the batch size.
   *
   * @return the batchsize, &le; 1 if no batching
   */
  public int getBatchSize() {
    return batchSize;
  }

  /**
   * Adds a statement and its related PDO to this batch.
   *
   * @param modType the statement type
   * @param statement the insert, update, or delete statement
   * @param object the PDO affected by the statement
   * @param rootClassId the root class ID
   */
  public void add(ModificationType modType, PreparedStatementWrapper statement, AbstractDbObject object, int rootClassId) {
    if (statement.getStatementKey() == null) {
      throw new PersistenceException(statement.getSession(), "batching not supported for one-shot prepared statements");
    }
    if (rootClassId != lastRootClassId || !objects.add(object)) {
      execute(true);    // order can change -> flush!
      lastRootClassId = rootClassId;
      objects.add(object);
    }
    statements.computeIfAbsent(statement.getStatementKey(), statementKey -> createBatchStatement(statement, modType)).add(object);
  }

  /**
   * Batches a statement for execution.<br>
   * When the session's batchsize is reached, all pending statements are executed in the order of registration
   * up to the given statement.
   *
   * @param statement the statement
   */
  public void batch(PreparedStatementWrapper statement) {
    if (statement.getBatchCount() >= batchSize - 1) {   // -1 due to addBatch in DbBatchStatement#execute
      executePending(false, statement);
    }
  }

  /**
   * Executes all pending statements.<br>
   * Statements with more than one parameter set are executed in batch mode,
   * otherwise in standard mode.
   *
   * @param flush true to forget all statements and objects as well
   */
  public void execute(boolean flush) {
    executePending(true, null);
    if (flush) {
      statements.clear();
      objects.clear();
    }
  }

  /**
   * Creates a batched statement.
   *
   * @param statement the prepared statement
   * @param modType the statement type
   * @return the batched statement
   */
  protected DbBatchStatement createBatchStatement(PreparedStatementWrapper statement, ModificationType modType) {
    statement.setBatch(this);
    return new DbBatchStatement(statement, modType);
  }

  /**
   * Executes all pending batched statements.
   *
   * @param finish true if this is the last invocation
   * @param upToStatement the statement in the batch to stop after its execution
   */
  protected void executePending(boolean finish, PreparedStatementWrapper upToStatement) {
    for (DbBatchStatement statement : statements.values()) {
      statement.execute(finish);
      if (statement.getStatement() == upToStatement) {
        break;
      }
    }
  }

}
