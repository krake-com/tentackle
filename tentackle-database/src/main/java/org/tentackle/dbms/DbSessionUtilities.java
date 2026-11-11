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
import org.tentackle.misc.Identifiable;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionUtilities;
import org.tentackle.sql.Backend;

import java.sql.SQLException;

/**
 * Replaces {@link SessionUtilities} to handle {@link java.sql.SQLException}s wrapped in a {@link PersistenceException}.
 * <ul>
 *   <li>exceptions are flagged temporary if the backend considers the SQL exception transient</li>
 *   <li>exception messages are extended by the SQL message, error code, and SQL state</li>
 * </ul>
 */
@Service(SessionUtilities.class)
public class DbSessionUtilities extends SessionUtilities {

  /**
   * Creates the session utilities.
   */
  public DbSessionUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void alignTemporaryExceptionStatus(PersistenceException exception) {
    if (exception.getSession() instanceof Db) {    // just for sure...
      Throwable cause = exception.getCause();
      if (cause instanceof SQLException) {    // local session!
        Backend backend = ((Db) exception.getSession()).getBackend();
        exception.setTemporary(backend.isTransientTransactionException((SQLException) cause));
      }
    }
  }

  @Override
  public StringBuilder createLazyExceptionMessage(String msg, Throwable cause, Identifiable identifiable, Session session) {
    StringBuilder buf = super.createLazyExceptionMessage(msg, cause, identifiable, session);
    if (cause instanceof SQLException sx) {
      buf.append("\nSQL-Message: ").append(sx.getMessage())
         .append("\nSQL-Code:    ").append(sx.getErrorCode())
         .append("\nSQL-State:   ").append(sx.getSQLState());
    }
    return buf;
  }

  @Override
  protected boolean isMultiLineExceptionMessage(String msg, Throwable cause, Identifiable identifiable, Session session) {
    return super.isMultiLineExceptionMessage(msg, cause, identifiable, session) || cause instanceof SQLException;
  }

  @Override
  public synchronized void terminateHelperThreads() {
    DbTransactionFactory.getInstance().requestTermination();
    super.terminateHelperThreads();
  }
}
