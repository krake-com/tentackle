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

package org.tentackle.trip;

import java.util.UUID;

/**
 * Remote interface to look up named remote objects.<br>
 * Implemented by the server's {@link Registry}, which is always available under {@link Registry#REGISTRY_ID}.
 *
 * @see Transport#lookup(Class, String)
 */
public interface RemoteLookup extends Remote {

  /**
   * Looks up the unique ID of a remote delegate by its symbolic name.
   *
   * @param name the symbolic name of the remote delegate to look up
   * @return the unique ID of the remote delegate if found, null otherwise
   */
  UUID lookup(String name);

}
