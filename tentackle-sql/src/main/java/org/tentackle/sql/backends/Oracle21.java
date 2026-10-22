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

import java.sql.Types;

/**
 * Backend for Oracle versions 21c or newer.<br>
 * Requires a URL with type postfix {@code "|Oracle21"}, since the same URL as {@link Oracle} matches.
 *
 * @author harald
 */
@Service(Backend.class)
public class Oracle21 extends Oracle {

  /**
   * Creates the Oracle21 backend.
   */
  public Oracle21() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean isMatchingUrl(String url) {
    return false;   // same URL as Oracle -> select by name
  }

  @Override
  public String getName() {
    return "Oracle21";
  }

  @Override
  public void validateVersion(BackendVersionInfo versionInfo) {
    if (versionInfo.majorVersion() < 21) {
      throw new BackendException(this + " needs at least Oracle 21, but is connected to " + versionInfo +
                                 " (remove '|Oracle21' from the connection URL if the server is older)");
    }
  }

  @Override
  public boolean isJsonSupported() {
    return true;   // native JSON column type, available since 21c
  }

  @Override
  public String sqlTypeToString(SqlType sqlType, int size) {
    if (sqlType == SqlType.JSON) {
      return TYPE_JSON;
    }
    return super.sqlTypeToString(sqlType, size);
  }

  @Override
  public SqlType[] jdbcTypeToSqlType(int jdbcType, String typeName, int size, int scale) {
    if (jdbcType == Types.OTHER && TYPE_JSON.equalsIgnoreCase(typeName)) {
      return new SqlType[] { SqlType.JSON };
    }
    return super.jdbcTypeToSqlType(jdbcType, typeName, size, scale);
  }

}
