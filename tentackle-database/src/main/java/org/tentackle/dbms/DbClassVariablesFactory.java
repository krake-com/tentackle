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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;


interface DbClassVariablesFactoryHolder {
  DbClassVariablesFactory INSTANCE = ServiceFactory.createService(DbClassVariablesFactory.class, DbClassVariablesFactory.class);
}


/**
 * Factory for class variables.
 */
@Service(DbClassVariablesFactory.class)   // defaults to self
public class DbClassVariablesFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static DbClassVariablesFactory getInstance() {
    return DbClassVariablesFactoryHolder.INSTANCE;
  }


  /**
   * Creates the class variables factory.
   */
  public DbClassVariablesFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Creates a class variable for a db object.
   *
   * @param clazz is the class of the derived AbstractDbObject
   * @param classId the unique class id, 0 if abstract
   * @param tableName is the SQL table name
   * @param <P> the persistence type
   */
  public <P extends AbstractDbObject<P>> DbObjectClassVariables<P> dbPoCv(Class<P> clazz, int classId, String tableName) {
    return new DbObjectClassVariables<>(clazz, classId, tableName);
  }

  /**
   * Creates a class variable for a db object.<br>
   * ClassId and tablename are derived from mapped services in META-INF.
   *
   * @param clazz is the class of the derived AbstractDbObject
   * @param <P> the persistence type
   */
  public <P extends AbstractDbObject<P>> DbObjectClassVariables<P> dbPoCv(Class<P> clazz) {
    return new DbObjectClassVariables<>(clazz);
  }

  /**
   * Creates a class variable for a db operation.
   *
   * @param clazz is the class of the derived AbstractDbOperation
   * @param <P> the persistent operation class
   */
  public <P extends AbstractDbOperation<P>> DbOperationClassVariables<P> dbOpCv(Class<P> clazz)  {
    return new DbOperationClassVariables<>(clazz);
  }

}
