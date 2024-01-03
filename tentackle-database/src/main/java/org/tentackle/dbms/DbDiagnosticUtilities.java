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
import org.tentackle.misc.DiagnosticUtilities;

import java.lang.management.ThreadInfo;
import java.util.Collection;

/**
 * Utilities for diagnostic purposes with persistence extensions.
 * <p>
 * Adds extra information to the thread stack-dump.
 *
 * @author harald
 */
@Service(DiagnosticUtilities.class)
public class DbDiagnosticUtilities extends DiagnosticUtilities {

  /**
   * Creates the diagnostic utilities.
   */
  public DbDiagnosticUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  protected void doCreateStackDump(StringBuilder buf, ThreadInfo info, Thread thread) {

    super.doCreateStackDump(buf, info, thread);

    // check all attached connections
    for (ManagedConnection connection: ManagedConnectionMonitor.getInstance().getManagedConnections()) {
      Db db = connection.getSession();
      if (db != null) {
        // still attached
        Thread ownerThread = db.getOwnerThread();
        if (ownerThread != null && ownerThread.threadId() == info.getThreadId()) {
          // this thread belongs to the connection
          buf.append("    connection: ");
          doCreateConnectionDump(buf, connection);
          buf.append("\n\n");
          break;
        }
      }
    }
  }


  /**
   * Creates the dump for a connection.
   *
   * @param buf the string buffer
   * @param connection the managed connection attached to a session owned by a thread.
   */
  protected void doCreateConnectionDump(StringBuilder buf, ManagedConnection connection) {
    buf.append(connection.toDiagnosticString());
  }


  @Override
  protected void doCreateStackDump(StringBuilder buf) {

    super.doCreateStackDump(buf);

    Collection<Db> openDbs = Db.getAllOpenSessions();
    if (!openDbs.isEmpty()) {
      buf.append("\n\nopen Db sessions:\n");
      // log all open Db sessions including those that have not set the current owner thread
      for (Db db: openDbs) {
        buf.append("\n    ");
        buf.append(db);
        buf.append(": ");
        ManagedConnection con = db.getConnection();
        if (con != null) {
          doCreateConnectionDump(buf, con);
          buf.append('\n');
        }
        else  {
          buf.append("not attached\n");
        }
      }
    }
  }

}
