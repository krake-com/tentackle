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

package org.tentackle.pdo;

import org.tentackle.common.Constants;
import org.tentackle.log.Logger;
import org.tentackle.reflect.Interception;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method as transactional.
 * <p>
 * Methods of the PDO- or Operation-interfaces annotated
 * with {@code @Transaction} will be enclosed by a transaction.
 * If there is no transaction running, a new transaction is started immediately before the method is invoked.
 * Any exception within that method will roll back the transaction. Otherwise, the transaction will be
 * committed after the method's execution.<br>
 * If there is already a transaction running, the method is executed within that transaction.
 * <p>
 * Another use case is to ensure that a transaction is already running via {@code running=true} and/or
 * the method is invoked within a certain transaction (additionally specified transaction name).
 *
 * @see NoTransaction
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Interception(implementedBy=TransactionInterceptor.class, type = Interception.Type.PUBLIC)
public @interface Transaction {

  /**
   * The transaction name.<br>
   * By default, the name will be derived from the annotated method.
   *
   * @return the optional transaction name
   */
  String value() default Constants.NAME_UNKNOWN;

  /**
   * Requires that a transaction is already running.<br>
   * If the optional transaction name is given, it must match the name of the current transaction.
   *
   * @return true if transaction must be running already, false is default
   */
  boolean running() default false;

  /**
   * Determines whether rollbacks should be logged in detail or not.<br>
   * By default, rollbacks are logged with detailed diagnostics, such as executed statements, durations, and more,
   * if they are caused by a non-temporary {@link org.tentackle.session.PersistenceException} (for example, due to a constraint violation).
   * This is desired for most of such rollbacks since they are unexpected. Certain transactions, however, are expected
   * to fail most of the time. In such cases, the amount of logging can be reduced significantly by setting this flag to true.
   * <p>
   * Please notice that rollbacks caused by temporary or other exceptions (application-specific, domain related),
   * are always rolled back silently.
   *
   * @return true to suppress detailed logging for rollbacks, default is false
   */
  boolean rollbackSilently() default false;

  /**
   * The transaction isolation level.
   *
   * @return the optional isolation
   * @see #retry()
   */
  TransactionIsolation isolation() default TransactionIsolation.DEFAULT;

  /**
   * The transaction writability.
   *
   * @return the optional writability
   */
  TransactionWritability writability() default TransactionWritability.DEFAULT;

  /**
   * Determines whether deferrable constraints should be deferred to the end of the transaction.<br>
   * If true, {@link org.tentackle.session.Session#setConstraintsDeferred(boolean)} is invoked with
   * {@code true} right after the transaction is started (only if this call actually started a new
   * transaction, i.e. not when joining an already running one). Requires a backend supporting it
   * (see {@code isSetConstraintsDeferredSupported} in the SQL backend) and at least one
   * deferrable unique index or foreign key (see the model's {@code deferred}/{@code deferrable}
   * index keywords and model defaults).
   *
   * @return true to defer deferrable constraints, false is default
   */
  boolean deferred() default false;

  /**
   * Determines whether a transaction rolled back due to a temporary exception should be retried.<br>
   * By default, transactions will not be retried.<br>
   * Isolation levels {@link TransactionIsolation#REPEATABLE_READ} and {@link TransactionIsolation#SERIALIZABLE}
   * may produce database serialization errors and throw transient SQL exceptions, which usually
   * requires retryable transactions to work reliably in production.<br>
   * Another source for such temporary exceptions is database deadlocks.<br>
   * The Tentackle persistence layer will also mark {@link org.tentackle.session.NotFoundException}s as temporary,
   * if an optimistic locking error is detected (serial mismatch). The same applies to the pessimistic {@link LockException}.
   * <p>
   * CAUTION: retryable transactions must be idempotent except for database modifications! This basically means
   * that all code executed within such a transaction must not modify the conditions that exist at the beginning
   * of its execution. Whenever a temporary exception is thrown, the modifications of the database are rolled back,
   * but the state of the involved java objects is left unchanged!
   *
   * @return true if retry transaction after transient exceptions
   */
  boolean retry() default false;

  /**
   * Defines the retry policy.<br>
   * Only used if {@link #retry()} is true.
   * The empty string denotes the default policy.
   * An unknown policy name fails with a {@link org.tentackle.session.PersistenceException}
   * before the transaction begins.
   *
   * @return the policy name
   * @see DefaultTransactionRetryPolicy
   */
  String retryPolicy() default "";

  /**
   * The log level to log retries.
   *
   * @return the log level
   */
  Logger.Level retryLogLevel() default Logger.Level.WARNING;

}
