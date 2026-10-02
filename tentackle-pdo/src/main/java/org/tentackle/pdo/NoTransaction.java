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

import org.tentackle.reflect.Interception;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to verify that there is no transaction currently running.
 * <p>
 * This may be useful for methods providing stale data, for example cached or lazy references.
 * <p>
 * Another use case is to make sure that a new transaction is always started. This can be accomplished
 * by a {@code @NoTransaction} annotation followed by a {@code @Transaction} annotation.
 *
 * @see Transaction
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Interception(implementedBy=NoTransactionInterceptor.class, type = Interception.Type.PUBLIC)
public @interface NoTransaction {

  /**
   * Transactions must be avoided only if the PDO is cached.<br>
   * For components, the cache status of the root entity is checked.
   * If set, the annotated object must be a PDO.
   *
   * @return true if cached only, default is false
   */
  boolean cachedOnly() default false;

}
