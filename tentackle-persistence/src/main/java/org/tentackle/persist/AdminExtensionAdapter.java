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
import org.tentackle.pdo.AdminExtension;
import org.tentackle.pdo.DomainContext;
import org.tentackle.persist.trip.AdminExtensionAdapterRemoteDelegate;
import org.tentackle.session.PersistenceException;

import java.util.List;

/**
 * Client-side adapter for the admin session extension.<br>
 * Delegates all methods to the {@link AdminExtensionAdapterRemoteDelegate} on the server.
 * Hence, the adapter requires a remote session.
 * <p>
 * The adapter is obtained via {@link org.tentackle.pdo.PdoRemoteSession#getExtension}.
 *
 * @author harald
 */
@Service(AdminExtension.class)
public class AdminExtensionAdapter implements AdminExtension {

  private DomainContext context;
  private AdminExtensionAdapterRemoteDelegate adminExt;

  /**
   * Creates the adapter.
   */
  public AdminExtensionAdapter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * {@inheritDoc}
   * <p>
   * Invoked from {@link PdoRemoteSessionAdapter}.
   * The context can be set only once and is immutable afterward.
   *
   * @param context the domain context of the client user (must be connected to a remote session)
   * @throws PersistenceException if the context is already set
   */
  @Override
  public void setDomainContext(DomainContext context) {
    if (this.context != null) {
      throw new PersistenceException("context can be set only once!");
    }
    this.context = context;
    PdoRemoteSessionAdapter adapter = (PdoRemoteSessionAdapter) context.getSession().getRemoteSession();
    adminExt = adapter.getRemoteDbSession().getRemoteDelegate(getClass().getName());
  }


  @Override
  public List<SessionData> getSessions() {
    return adminExt.getSessions(context);
  }

  @Override
  public boolean logout(long userId, long sessionGroupId, String applicationName, long applicationId) {
    return adminExt.logout(context, userId, sessionGroupId, applicationName, applicationId);
  }

  @Override
  public int kill(long userId, long sessionGroupId, String applicationName,  long applicationId) {
    return adminExt.kill(context, userId, sessionGroupId, applicationName, applicationId);
  }


  // ---- implements DomainContextDependable ----------------------------------

  @Override
  public void determineContextId() {
  }

  @Override
  public long getContextId() {
    return context.getContextId();
  }

  @Override
  public DomainContext getBaseContext() {
    return getDomainContext();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Not supported by the adapter.
   *
   * @throws PersistenceException always
   */
  @Override
  public DomainContext createValidContext() {
    throw new PersistenceException("illegal attempt to create a context");
  }

  /**
   * {@inheritDoc}
   * <p>
   * The adapter's context is always immutable.
   *
   * @return always true
   */
  @Override
  public boolean isDomainContextImmutable() {
    return true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The adapter's context is always immutable.
   *
   * @throws PersistenceException if {@code contextImmutable} is false
   */
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
