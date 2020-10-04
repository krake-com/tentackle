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

package org.tentackle.sql.backends;

import org.tentackle.common.Service;
import org.tentackle.sql.Backend;

/**
 * Backend for MariaDB.
 *
 * @author harald
 */
@Service(Backend.class)
public class MariaDb extends MySql {

  @Override
  public boolean isMatchingUrl(String url) {
    return url.contains(":mariadb");
  }

  @Override
  public String getName() {
    return "MariaDB";
  }

  @Override
  public String getDriverClassName() {
    return "org.mariadb.jdbc.Driver";
  }

  @Override
  protected boolean isDropIfExistsSupported() {
    return true;
  }
}
