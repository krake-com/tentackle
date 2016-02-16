/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.pdo.pdofactorytest;

import org.tentackle.pdo.mock.MockPersistentOperation;
import org.testng.Reporter;


/**
 * Operation persistence implementation.
 *
 * @author harald
 */
public class SomeOperationPersistenceImpl extends MockPersistentOperation<SomeOperation> implements SomeOperationPersistence {

  /**
   * Creates an operation persistence object.
   *
   * @param operation the operation
   * @param context the domain context
   */
  public SomeOperationPersistenceImpl(SomeOperation operation) {
    super(operation);
    Reporter.log("SomeOperationPersistenceImpl created<br/>");
  }

  @Override
  public String echo(String str) {
    return str;
  }

}
