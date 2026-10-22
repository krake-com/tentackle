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

package org.tentackle.jta;

import org.tentackle.dbms.ConnectionManager;
import org.tentackle.sql.BackendInfo;

/**
 * Runs the {@link JoinModeTest} with multiplexed XA connections.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MpxJoinModeTest extends JoinModeTest {

  @Override
  protected ConnectionManager createConnectionManager(String name, BackendInfo backendInfo) {
    return new XaMpxConnectionManager(name, backendInfo, 10, 1, 1, 1, 1, 2, 720, 2160);
  }

}
