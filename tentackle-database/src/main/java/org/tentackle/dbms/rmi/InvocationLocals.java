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

package org.tentackle.dbms.rmi;

import java.util.Locale;
import org.tentackle.common.LocaleProvider;
import org.tentackle.session.Session;

/**
 * Holds the old thread-locals during remote delegate method invocation.
 *
 * @author harald
 */
public class InvocationLocals {

  private final RemoteDelegateImpl<?> delegate;

  private final Thread ownerThread;
  private final Session currentSession;
  private final Locale currentLocale;


  /**
   * Creates and retrieves the current settings.
   *
   * @param delegate the remote delegate
   * @param mdc the mapped diagnostic context
   * @param mdcKey the MDC key
   */
  public InvocationLocals(RemoteDelegateImpl<?> delegate) {
    this.delegate = delegate;
    ownerThread = delegate.getSession().getOwnerThread();
    currentSession = Session.getCurrentSession();
    currentLocale = LocaleProvider.getInstance().getCurrentLocale();
  }


  /**
   * Restores the thread-locals, session's ownerthread and MDC.
   */
  public void restore() {
    Session.setCurrentSession(currentSession);
    delegate.getSession().setOwnerThread(ownerThread);
    LocaleProvider.getInstance().setCurrentLocale(currentLocale);
  }

}
