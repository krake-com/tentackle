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

import org.tentackle.dbms.DbObjectClassVariables;
import org.tentackle.dbms.DbOperationClassVariables;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.OperationMethodCache;

/**
 * Extends {@link DbObjectClassVariables} for {@link AbstractPersistentOperation}s.
 *
 * @param <T> the operation class type
 * @param <P> the persistence class type
 * @author harald
 */
public class PersistentOperationClassVariables<T extends Operation<T>, P extends AbstractPersistentOperation<T,P>> extends DbOperationClassVariables<P> {

  /**
   * Creates an operation class variable.
   *
   * @param operationClass the operation's class (i.e. interface)
   * @param clazz the class of the derived operation
   * @param <T> the operation class type
   * @param <P> the persistence class type
   */
  public static <T extends Operation<T>, P extends AbstractPersistentOperation<T,P>> PersistentOperationClassVariables<T,P> create(
      Class<T> operationClass, Class<P> clazz)  {
    return PersistentClassVariablesFactory.getInstance().opCv(operationClass, clazz);
  }



  /**
   * The associated operation class (i.e. the interface)
   */
  public final Class<T> operationClass;

  /**
   * The PDO method invocation cache.
   */
  public final OperationMethodCache<T> methodCache;


  /**
   * Constructs a classvariable.
   *
   * @param operationClass the operation's class (i.e. interface)
   * @param clazz the class of the derived operation
   */
  public PersistentOperationClassVariables(Class<T> operationClass, Class<P> clazz)  {
    super(clazz);
    this.operationClass = operationClass;
    this.methodCache = new OperationMethodCache<>(operationClass);
  }

}
