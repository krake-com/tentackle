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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to flag a method as a transaction.
 * <p>
 * Methods of the PDO- or Operation-interfaces annotated
 * with {@code @Transaction} will be encapsulated by a transaction.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Interception(implementedBy=TransactionInterceptor.class)
public @interface Transaction {

  /**
   * The transaction name.
   *
   * @return the optional name
   */
  String value() default Constants.NAME_UNKNOWN;

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
   * Determines whether a transaction that receives a transient exception from the backend should be retried.<br>
   * By default, transactions will not be retried.<br>
   * Isolation levels {@link TransactionIsolation#REPEATABLE_READ} and {@link TransactionIsolation#SERIALIZABLE} usually
   * require retryable transactions to work reliably in production.
   * <p>
   * CAUTION: retryable transactions must be idempotent except for database modifications! This basically means,
   * that all code executed within such a transaction must not modify the conditions that exist at the beginning
   * of its execution. Whenever a transient exception is thrown, the modifications of the database are rolled back,
   * but the state of the involved java objects is left unchanged.
   *
   * @return true if retry transaction after transient exceptions
   */
  boolean retry() default false;

  /**
   * Defines the retry policy.
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

