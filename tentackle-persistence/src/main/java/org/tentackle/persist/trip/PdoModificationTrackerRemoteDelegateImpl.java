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

package org.tentackle.persist.trip;

import org.tentackle.dbms.trip.DbModificationTrackerRemoteDelegateImpl;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.persist.PdoModificationTracker;

/**
 * Remote delegate implementation for {@link PdoModificationTracker}.
 *
 * @param <T> the modification tracker class
 * @author  harald
 */
public class PdoModificationTrackerRemoteDelegateImpl<T extends PdoModificationTracker>
             extends DbModificationTrackerRemoteDelegateImpl<T>
             implements PdoModificationTrackerRemoteDelegate {

  /**
   * Creates the remote delegate for the modification tracker.
   *
   * @param session the TRIP session
   * @param clazz the modification tracker class
   */
  public PdoModificationTrackerRemoteDelegateImpl(RemoteDbSessionImpl session, Class<T> clazz) {
    super(session, clazz);
  }

}
