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

import org.tentackle.trip.TripInstanceCreatorService;
import org.tentackle.trip.creators.AbstractThrowableInstanceCreator;

import java.sql.BatchUpdateException;
import java.sql.ClientInfoStatus;
import java.sql.DataTruncation;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.util.Map;

/**
 * Instance creator for {@link SQLException} and its subclasses.<br>
 * The exception is created via the public {@code (String reason, String SQLState, int vendorCode, Throwable cause)}
 * constructor of its class, which all subclasses of the {@code java.sql} package provide.
 * If the class does not provide it, for example, a driver-specific exception, a plain {@link SQLException} is created.
 * {@link BatchUpdateException}, {@link DataTruncation} and {@link SQLClientInfoException} are created with their
 * specific values.
 *
 * @param <T> the exception type
 * @see SQLExceptionComponentProvider
 */
@TripInstanceCreatorService(SQLException.class)
public class SQLExceptionInstanceCreator<T extends SQLException> extends AbstractThrowableInstanceCreator<T> {

  /**
   * Creates the instance creator.
   */
  public SQLExceptionInstanceCreator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T create(Class<T> clazz, Values values) throws ReflectiveOperationException {
    String message = values.get("message");
    String sqlState = values.get("sQLState");
    int vendorCode = values.getInt("errorCode", 0);
    Throwable cause = values.get("cause");
    T exception = null;

    if (BatchUpdateException.class.isAssignableFrom(clazz)) {
      long[] updateCounts = values.get("largeUpdateCounts");
      if (updateCounts == null) {
        int[] counts = values.get("updateCounts");
        if (counts != null) {
          updateCounts = new long[counts.length];
          for (int i = 0; i < counts.length; i++) {
            updateCounts[i] = counts[i];
          }
        }
      }
      exception = newInstance(clazz, new Class<?>[] { String.class, String.class, int.class, long[].class, Throwable.class },
                              message, sqlState, vendorCode, updateCounts, cause);
      if (exception == null) {
        exception = (T) new BatchUpdateException(message, sqlState, vendorCode, updateCounts, cause);
      }
    }
    else if (DataTruncation.class.isAssignableFrom(clazz)) {
      Class<?>[] parameterTypes = { int.class, boolean.class, boolean.class, int.class, int.class, Throwable.class };
      Object[] args = { values.getInt("index", -1), values.getBoolean("parameter"), values.getBoolean("read"),
                        values.getInt("dataSize", -1), values.getInt("transferSize", -1), cause };
      exception = newInstance(clazz, parameterTypes, args);
      if (exception == null) {
        exception = (T) DataTruncation.class.getConstructor(parameterTypes).newInstance(args);
      }
    }
    else if (SQLClientInfoException.class.isAssignableFrom(clazz)) {
      Map<String, ClientInfoStatus> failedProperties = values.get("failedProperties");
      exception = newInstance(clazz, new Class<?>[] { String.class, String.class, int.class, Map.class, Throwable.class },
                              message, sqlState, vendorCode, failedProperties, cause);
      if (exception == null) {
        exception = (T) new SQLClientInfoException(message, sqlState, vendorCode, failedProperties, cause);
      }
    }

    if (exception == null) {
      exception = newInstance(clazz, new Class<?>[] { String.class, String.class, int.class, Throwable.class },
                              message, sqlState, vendorCode, cause);
      if (exception == null) {
        exception = (T) new SQLException(message, sqlState, vendorCode, cause);
      }
    }

    SQLException next = values.get("nextException");
    if (next != null) {
      exception.setNextException(next);
    }
    return exception;
  }

}
