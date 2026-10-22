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

package org.tentackle.persist;

import org.tentackle.pdo.PersistentOperation;
import org.tentackle.pdo.Transaction;

/**
 * Persistence interface of {@link DemoOperation}.
 */
public interface DemoOperationPersistence extends PersistentOperation<DemoOperation> {

  /**
   * Runs the operation.
   *
   * @return the result
   */
  String execute();

  /**
   * Runs within a retryable transaction and fails the given number of times.
   *
   * @param failures the number of attempts that fail
   * @param temporary true if the failures are temporary exceptions
   * @return the number of attempts until success
   */
  @Transaction(value = "retry", retry = true, retryPolicy = "test")
  int executeWithRetry(int failures, boolean temporary);

}
