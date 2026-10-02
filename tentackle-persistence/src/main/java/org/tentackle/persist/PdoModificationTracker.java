/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.app.Application;
import org.tentackle.common.Service;
import org.tentackle.dbms.DbModificationTracker;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;

/**
 * The modification tracker for the PDO persistence layer.<br>
 * Replaces the {@link DbModificationTracker} of the database layer to make it PDO-aware.
 *
 * @author harald
 */
@Service(ModificationTracker.class)
public class PdoModificationTracker extends DbModificationTracker {

  /**
   * Creates the PDO tracker.
   * <p>
   * Notice that the tracker is a singleton.
   */
  public PdoModificationTracker() {
    super("PDO Modification Tracker");
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to map PDO interfaces to their table names.
   */
  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public IdSerialTuple getIdSerialForClass(Class<?> clazz) {
    if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
      return getIdSerialForName(PdoUtilities.getInstance().getTableName((Class) clazz));
    }
    return super.getIdSerialForClass(clazz);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to enable the local client mode for non-server applications connected directly to the database.
   */
  @Override
  public void setSession(Session session) {
    super.setSession(session);

    // enable local client mode if this is a non-server application with a local JDBC URL.
    // in all other cases use optimized mode.
    Application application = Application.getInstance();
    setLocalClientMode(!session.isRemote() && application != null && !application.isServer());
  }

}
