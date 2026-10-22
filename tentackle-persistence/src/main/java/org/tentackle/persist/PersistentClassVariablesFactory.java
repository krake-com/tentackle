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

import org.tentackle.common.Service;
import org.tentackle.dbms.DbClassVariablesFactory;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.List;

interface PersistentClassVariablesFactoryHolder {
  PersistentClassVariablesFactory INSTANCE = (PersistentClassVariablesFactory) DbClassVariablesFactory.getInstance();
}

/**
 * Factory for class variables.<br>
 * Replaces and extends the {@link DbClassVariablesFactory} of the database layer
 * to create the class variables for PDOs and operations as well.
 */
@Service(DbClassVariablesFactory.class)   // replaces and extends DbClassVariablesFactory
public class PersistentClassVariablesFactory extends DbClassVariablesFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static PersistentClassVariablesFactory getInstance() {
    return PersistentClassVariablesFactoryHolder.INSTANCE;
  }


  /**
   * Creates the class variables factory.
   */
  public PersistentClassVariablesFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Creates PDO class variables.
   *
   * @param pdoClass the PDO's class (i.e., interface)
   * @param poClass the class of the persistence implementation
   * @param tableAlias the table alias to be used in joined selects
   * @param superClassVariables the class variables of the superclass, null if not inherited
   * @param eagerJoins the optional eager joins, null or empty if none
   * @param <T> the PDO class type
   * @param <P> the persistence class type
   * @return the class variables
   */
  public <T extends PersistentDomainObject<T>, P extends AbstractPersistentObject<T,P>> PersistentObjectClassVariables<T,P> pdoCv(
      Class<T> pdoClass, Class<P> poClass, String tableAlias,
      PersistentObjectClassVariables<? super T, ? super P> superClassVariables,
      List<Join<T,?>> eagerJoins)  {

    return new PersistentObjectClassVariables<T,P>(pdoClass, poClass, tableAlias, superClassVariables, eagerJoins);
  }

  /**
   * Creates PDO class variables.
   *
   * @param pdoClass the PDO's class (i.e., interface)
   * @param poClass the class of the persistence implementation
   * @param tableAlias the table alias to be used in joined selects
   * @param <T> the PDO class type
   * @param <P> the persistence class type
   * @return the class variables
   */
  public <T extends PersistentDomainObject<T>, P extends AbstractPersistentObject<T,P>>  PersistentObjectClassVariables<T,P> pdoCv(
      Class<T> pdoClass, Class<P> poClass, String tableAlias)  {
    return new PersistentObjectClassVariables<>(pdoClass, poClass, tableAlias);
  }

  /**
   * Creates operation class variables.
   *
   * @param operationClass the operation's class (i.e., interface)
   * @param clazz the class of the derived operation
   * @param <T> the operation class type
   * @param <P> the persistence class type
   * @return the class variables
   */
  public <T extends Operation<T>, P extends AbstractPersistentOperation<T,P>> PersistentOperationClassVariables<T,P> opCv(
      Class<T> operationClass, Class<P> clazz)  {
    return new PersistentOperationClassVariables<>(operationClass, clazz);
  }

}
