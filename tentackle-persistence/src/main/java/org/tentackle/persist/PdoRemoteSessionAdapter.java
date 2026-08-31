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

import org.tentackle.common.ServiceFactory;
import org.tentackle.dbms.RemoteSessionAdapter;
import org.tentackle.dbms.trip.RemoteDbSession;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextDependable;
import org.tentackle.pdo.PdoRemoteSession;
import org.tentackle.security.SecurityException;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityManager;
import org.tentackle.security.SecurityResult;
import org.tentackle.session.PersistenceException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Extended remote session adapter providing PDO-related services.
 *
 * @author harald
 */
public class PdoRemoteSessionAdapter extends RemoteSessionAdapter implements PdoRemoteSession {

  /**
   * Creates the remote session adapter.
   *
   * @param rs the remote session object
   */
  public PdoRemoteSessionAdapter(RemoteDbSession rs) {
    super(rs);
  }

  @Override
  public <T extends DomainContextDependable> T getExtension(DomainContext context, Class<T> extIf) {
    // Important: the server should its own checks for each method invocation!
    SecurityFactory sf = SecurityFactory.getInstance();
    SecurityManager sm = sf.getSecurityManager();
    SecurityResult sr = sm.evaluate(context, sf.getExecutePermission(), extIf);
    if (!sr.isAccepted()) {
      throw new SecurityException(sr.explain("no execute permission for " + extIf.getName()));
    }
    T adapter = ServiceFactory.createService(extIf);
    try {
      /*
       * The adapter must implement DomainContextDependable and thus provides the method:
       * void setDomainContext(DomainContext context)
       */
      Method m = adapter.getClass().getMethod("setDomainContext", DomainContext.class);
      m.invoke(adapter, context);
      return adapter;
    }
    catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException |
           InvocationTargetException ex) {
      throw new PersistenceException("could not create adapter for " + extIf, ex);
    }
  }

}
