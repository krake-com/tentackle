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

package org.tentackle.dbms.trip;

import org.tentackle.common.NamingRules;
import org.tentackle.common.Service;
import org.tentackle.dbms.AbstractDbOperation;
import org.tentackle.session.PersistenceException;

/**
 * The default implementation for a {@link RemoteDbDelegateLocator}.
 *
 * @author harald
 */
@Service(RemoteDbDelegateLocator.class)
public class DbRemoteDelegateLocator implements RemoteDbDelegateLocator {

  private record DefaultResult(Class<?> effectiveClass,
                               Class<RemoteDbDelegate> remoteDelegate,
                               Class<RemoteDbDelegateImpl<?>> remoteDelegateImpl) implements Result {
  }

  private final NamingRules namingRules = NamingRules.getInstance();

  /**
   * Creates a remote delegate locator.
   */
  public DbRemoteDelegateLocator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings("unchecked")
  public Result findRemoteDelegate(Class<?> clazz) throws ClassNotFoundException {

    // take package from serviced class
    String className = clazz.getName();
    int ndx = className.lastIndexOf('.');
    String pkgName = className.substring(0, ndx);

    // take classname from effective class
    Class<?> effectiveClazz = findEffectiveClass(clazz);
    String effectiveClassName = effectiveClazz.getName();
    ndx = effectiveClassName.lastIndexOf('.');
    String clsName = effectiveClassName.substring(ndx + 1);

    boolean isOperation = isOperation(effectiveClazz);

    String delegateName =
      isOperation ?
        namingRules.getOperationRemoteInterfacePackageName(pkgName) + '.' + namingRules.getOperationRemoteInterface(clsName) :
        namingRules.getPdoRemoteInterfacePackageName(pkgName) + '.' + namingRules.getPdoRemoteInterface(clsName);

    Class<?> delegateClass = Class.forName(delegateName);
    if (!delegateClass.isInterface()) {
      throw new PersistenceException(delegateClass + " is not an interface");
    }
    if (!RemoteDbDelegate.class.isAssignableFrom(delegateClass)) {
      throw new PersistenceException(delegateClass + " does not extend " + RemoteDbDelegate.class);
    }

    String delegateImplName =
      isOperation ?
        namingRules.getOperationRemoteImplementationPackageName(pkgName) + '.' + namingRules.getOperationRemoteImplementation(clsName) :
        namingRules.getPdoRemoteImplementationPackageName(pkgName) + '.' + namingRules.getPdoRemoteImplementation(clsName);

    Class<?> delegateImplClass = Class.forName(delegateImplName);
    if (delegateImplClass.isInterface()) {
      throw new PersistenceException(delegateImplClass + " is an interface");
    }
    if (!RemoteDbDelegateImpl.class.isAssignableFrom(delegateImplClass)) {
      throw new PersistenceException(delegateImplClass + " does not extend " + RemoteDbDelegateImpl.class);
    }

    return new DefaultResult(effectiveClazz,
                             (Class<RemoteDbDelegate>) delegateClass,
                             (Class<RemoteDbDelegateImpl<?>>) delegateImplClass);
  }


  /**
   * Gets the effective class.<br>
   *
   * @param clazz the serviced class
   * @return the effective serviced class
   */
  protected Class<?> findEffectiveClass(Class<?> clazz) {
    return clazz;
  }

  /**
   * Returns whether the given class is an operation or a PDO.
   *
   * @param clazz the implementation class
   * @return true if operation, false is PDO
   */
  protected boolean isOperation(Class<?> clazz) {
    return AbstractDbOperation.class.isAssignableFrom(clazz);
  }

}
