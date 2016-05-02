/*
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

package org.tentackle.persist;

import java.rmi.RemoteException;
import java.util.List;
import org.tentackle.common.Service;
import org.tentackle.pdo.AdminExtension;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.persist.rmi.AdminExtensionRemoteDelegate;

/**
 * Adapter hiding the rmi-stuff for the admin session extension.
 *
 * @author harald
 */
@Service(AdminExtension.class)
public class AdminExtensionAdapter implements AdminExtension {

  private DomainContext context;
  private AdminExtensionRemoteDelegate adminExt;

  /**
   * {@inheritDoc}
   * <p>
   * Invoked from {@link RemoteSessionAdapter}.
   *
   * @param context the domain context of the client user
   */
  @Override
  public void setDomainContext(DomainContext context) {
    if (this.context != null) {
      throw new PersistenceException("context can be set only once!");
    }
    this.context = context;
    try {
      RemoteSessionAdapter adapter = (RemoteSessionAdapter) context.getSession().getRemoteSession();
      adminExt = adapter.getRemoteDbSession().getRemoteDelegate(AdminExtension.class.getName());
    }
    catch (RemoteException rex) {
      throw PersistenceException.createFromRemoteException(this, rex);
    }
  }


  @Override
  public List<SessionData> getSessions() {
    try {
      return adminExt.getSessions(context);
    }
    catch (RemoteException rex) {
      throw PersistenceException.createFromRemoteException(this, rex);
    }
  }

  @Override
  public int kill(long userId, long sessionGroupId, String applicationName,  long applicationId) {
    try {
      return adminExt.kill(context, userId, sessionGroupId, applicationName, applicationId);
    }
    catch (RemoteException rex) {
      throw PersistenceException.createFromRemoteException(this, rex);
    }
  }


  // ---- implements DomainContextDependable ---------- (not used) --------------

  @Override
  public void determineContextId() {
  }

  @Override
  public long getContextId() {
    return context.getContextId();
  }

  @Override
  public DomainContext getBaseContext() {
    return context;
  }

  @Override
  public DomainContext createValidContext() {
    throw new PersistenceException("illegal attempt to create a context");
  }

  @Override
  public boolean isDomainContextImmutable() {
    return true;
  }

  @Override
  public void setDomainContextImmutable(boolean contextImmutable) {
    if (!contextImmutable) {
      throw new PersistenceException("illegal attempt to set domain context to mutable");
    }
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

}
