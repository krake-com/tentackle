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

package org.tentackle.dbms.rmi;

import org.tentackle.common.Service;
import org.tentackle.session.PersistenceException;

/**
 * The default implementation for a {@link RemoteDelegateLocator}.
 *
 * @author harald
 */
@Service(RemoteDelegateLocator.class)
public class DbRemoteDelegateLocator implements RemoteDelegateLocator {

  private static class DefaultResult implements Result {

    private final Class<?> effectiveClass;
    private final Class<RemoteDelegate> remoteDelegate;
    private final Class<RemoteDelegateImpl<?>> remoteDelegateImpl;

    private DefaultResult(Class<?> effectiveClass,
                          Class<RemoteDelegate> remoteDelegate,
                          Class<RemoteDelegateImpl<?>> remoteDelegateImpl) {
      this.effectiveClass = effectiveClass;
      this.remoteDelegate = remoteDelegate;
      this.remoteDelegateImpl = remoteDelegateImpl;
    }

    @Override
    public Class<?> getEffectiveClass() {
      return effectiveClass;
    }

    @Override
    public Class<RemoteDelegate> getRemoteDelegate() {
      return remoteDelegate;
    }

    @Override
    public Class<RemoteDelegateImpl<?>> getRemoteDelegateImpl() {
      return remoteDelegateImpl;
    }
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

    String delegateName = pkgName + ".rmi." + clsName + "RemoteDelegate";
    Class<?> delegateClass = Class.forName(delegateName);
    if (!delegateClass.isInterface()) {
      throw new PersistenceException(delegateClass + " is not an interface");
    }
    if (!RemoteDelegate.class.isAssignableFrom(delegateClass)) {
      throw new PersistenceException(delegateClass + " does not extend " + RemoteDelegate.class);
    }

    Class<?> delegateImplClass = Class.forName(delegateName + "Impl");
    if (delegateImplClass.isInterface()) {
      throw new PersistenceException(delegateImplClass + " is an interface");
    }
    if (!RemoteDelegateImpl.class.isAssignableFrom(delegateImplClass)) {
      throw new PersistenceException(delegateImplClass + " does not extend " + RemoteDelegateImpl.class);
    }

    return new DefaultResult(effectiveClazz,
                             (Class<RemoteDelegate>) delegateClass,
                             (Class<RemoteDelegateImpl<?>>) delegateImplClass);
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

}
