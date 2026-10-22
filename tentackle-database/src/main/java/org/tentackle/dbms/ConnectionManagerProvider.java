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

/**
 * A provider for a connection manager.<br>
 * Implemented by applications that provide the default connection manager for new local sessions,
 * for example, server applications using an {@link MpxConnectionManager}.
 *
 * @author harald
 */
public interface ConnectionManagerProvider {

  /**
   * Gets the connection manager.
   *
   * @return the connection manager, null to use the default
   */
  ConnectionManager getConnectionManager();

}
