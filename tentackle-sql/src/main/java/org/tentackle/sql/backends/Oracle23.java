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
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendVersionInfo;
import org.tentackle.sql.SqlType;

/**
 * Backend for Oracle versions 23c or newer.<br>
 * Requires a URL with type postfix {@code "|Oracle23"}, since the same URL as {@link Oracle} matches.
 *
 * @author harald
 */
@Service(Backend.class)
public class Oracle23 extends Oracle21 {

  /**
   * Creates the Oracle23 backend.
   */
  public Oracle23() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean isMatchingUrl(String url) {
    return false;   // same URL as Oracle -> select by name
  }

  @Override
  public String getName() {
    return "Oracle23";
  }

  @Override
  public void validateVersion(BackendVersionInfo versionInfo) {
    if (versionInfo.majorVersion() < 23) {
      throw new BackendException(this + " needs at least Oracle 23, but is connected to " + versionInfo +
                                 " (remove '|Oracle23' from the connection URL if the server is older)");
    }
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    if (sqlType == SqlType.BIT) {
      return TYPE_BOOLEAN;   // native BOOLEAN column type, available since 23c
    }
    return super.sqlTypeToString(sqlType, size);
  }

  @Override
  public String valueToLiteral(SqlType sqlType, Object value) {
    if (sqlType == SqlType.BIT && value instanceof Boolean booleanValue) {
      return booleanValue ? "TRUE" : "FALSE";
    }
    return super.valueToLiteral(sqlType, value);
  }

  @Override
  protected boolean isDropIfExistsSupported() {
    return true;   // DROP ... IF EXISTS, available since 23c
  }

}
