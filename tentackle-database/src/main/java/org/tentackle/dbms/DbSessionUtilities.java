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
import org.tentackle.session.Session;
import org.tentackle.session.SessionUtilities;

import java.sql.SQLException;

/**
 * Replaces {@link SessionUtilities} to handle {@link java.sql.SQLException}s for {@link org.tentackle.session.PersistenceException}.
 */
@Service(SessionUtilities.class)
public class DbSessionUtilities extends SessionUtilities {

  @Override
  public Throwable alignExceptionCause(Throwable t) {
    if (t instanceof SQLException) {
      StackTraceElement[] trace = t.getStackTrace();
      SQLException sx = (SQLException) t;
      // replace by java.sql.SQLException which definitely is in the classpath!
      t = new SQLException(sx.getMessage() + " (" + t.getClass().getName() + ")", sx.getSQLState(), sx.getErrorCode());
      t.setStackTrace(trace);
    }
    return t;
  }

  @Override
  public StringBuilder createLazyExceptionMessage(String msg, Throwable cause, Identifiable identifiable, Session session) {
    StringBuilder buf = super.createLazyExceptionMessage(msg, cause, identifiable, session);
    if (cause instanceof SQLException) {
      SQLException sx = (SQLException) cause;
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
}
