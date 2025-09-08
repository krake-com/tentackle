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

import org.tentackle.common.RemoteMethod;
import org.tentackle.reflect.AbstractInterceptor;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionProvider;

import java.lang.reflect.Method;

/**
 * Implementation for the {@link NoTransaction} interception.
 *
 * @author harald
 */
public final class NoTransactionInterceptor extends AbstractInterceptor {

  /**
   * Creates a {@link NoTransaction} interceptor.
   */
  public NoTransactionInterceptor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Object proceed(Object proxy, Method method, Object[] args,
                        Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {

    if (orgProxy instanceof SessionProvider) {
      Session session = ((SessionProvider) orgProxy).getSession();

      if (!(session.isRemote() && orgMethod.isAnnotationPresent(RemoteMethod.class)) && // see TransactionInterceptor
          session.isTxRunning()) {

        boolean cachedOnly = ((NoTransaction) getAnnotation()).cachedOnly();
        // could be an operation as well, hence the test
        PersistentDomainObject<?> pdo = orgProxy instanceof PdoProvider ?
                                        ((PdoProvider) orgProxy).getPdo() : null;
        if (pdo != null && !pdo.getPersistenceDelegate().isRootEntity()) {
          PersistentDomainObject<?> root = pdo.getPersistenceDelegate().getDomainContext().getRootEntity();
          if (root == null) {
            throw new PersistenceException("cannot determine root-entity for component-PDO " + pdo.toGenericString());
          }
          pdo = root;
        }
        if (cachedOnly && pdo == null) {
          throw new PersistenceException(EffectiveClassProvider.getEffectiveClass(orgProxy) + " is not a PDO");
        }
        if (!cachedOnly || pdo.isCached()) {
          StringBuilder buf = new StringBuilder();
          buf.append(orgMethod).append(" must not be used within a transaction!");
          boolean needClosing = false;
          if (session.getTxName() != null) {
            buf.append(" (txName=").append(session.getTxName());
            needClosing = true;
          }
          if (pdo != null) {
            buf.append(needClosing ? ", " : " (");
            if (pdo.isCached()) {
              buf.append("cached ");
            }
            buf.append("pdo=").append(pdo.toGenericString());
            needClosing = true;
          }
          if (needClosing) {
            buf.append(')');
          }
          throw new PersistenceException(buf.toString());
        }
      }

      // else ok:
      return method.invoke(proxy, args);
    }
    else  {
      throw new PersistenceException(EffectiveClassProvider.getEffectiveClass(orgProxy) +
                                     " is not a SessionProvider");
    }
  }

}
