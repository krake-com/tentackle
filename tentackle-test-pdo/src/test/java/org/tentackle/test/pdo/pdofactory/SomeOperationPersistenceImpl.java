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


package org.tentackle.test.pdo.pdofactory;

import org.testng.Reporter;

import org.tentackle.pdo.OperationMethodCache;
import org.tentackle.pdo.OperationMethodCacheProvider;
import org.tentackle.pdo.mock.MockPersistentOperation;

import java.io.Serial;


/**
 * Operation persistence implementation.
 *
 * @author harald
 */
public class SomeOperationPersistenceImpl extends MockPersistentOperation<SomeOperation>
       implements SomeOperationPersistence, OperationMethodCacheProvider<SomeOperation> {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final OperationMethodCache<SomeOperation> METHOD_CACHE = new OperationMethodCache<>(SomeOperation.class);

  /**
   * Creates an operation persistence object.
   *
   * @param operation the operation
   */
  public SomeOperationPersistenceImpl(SomeOperation operation) {
    super(operation);
    Reporter.log("SomeOperationPersistenceImpl created<br/>");
  }

  @Override
  public String echo(String str) {
    return str;
  }

  @Override
  public OperationMethodCache<SomeOperation> getOperationMethodCache() {
    return METHOD_CACHE;
  }
}
