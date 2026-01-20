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

package org.tentackle.dbms.trip;

import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripInstanceCreator;
import org.tentackle.trip.TripInstanceCreatorService;

import java.sql.SQLException;
import java.util.Map;

@TripInstanceCreatorService(SQLException.class)
public class SQLExceptionInstanceCreator implements TripInstanceCreator<SQLException> {

  public SQLExceptionInstanceCreator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public SQLException create(Class<SQLException> clazz) {
    return new SQLException();
  }

  @Override
  public SQLException create(Class<SQLException> clazz, Map<TripComponent<SQLException, ?>, Object> values) {
    String message = null;
    int vendorCode = 0;
    String sqlState = null;
    SQLException next = null;
    StackTraceElement[] stackTrace = null;
    for (Map.Entry<TripComponent<SQLException, ?>, Object> entry : values.entrySet()) {
      switch (entry.getKey().getName()) {
        case "message":
          message = (String) entry.getValue();
          break;
        case "errorCode":
          vendorCode = (Integer) entry.getValue();
          break;
        case "sQLState":
          sqlState = (String) entry.getValue();
          break;
        case "nextException":
          next = (SQLException) entry.getValue();
          break;
        case "stackTrace":
          stackTrace = (StackTraceElement[]) entry.getValue();
          break;
      }
    }
    SQLException exception = new SQLException(message, sqlState, vendorCode, next);
    if (stackTrace != null) {
      exception.setStackTrace(stackTrace);
    }
    return exception;
  }

  @Override
  public boolean isCreationFromValuesSupported(Class<SQLException> clazz) {
    return true;
  }

}
