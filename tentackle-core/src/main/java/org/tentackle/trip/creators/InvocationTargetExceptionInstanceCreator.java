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

package org.tentackle.trip.creators;

import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripInstanceCreator;
import org.tentackle.trip.TripInstanceCreatorService;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Instance creator for {@link InvocationTargetException}.<br>
 * The target exception can only be passed to the constructor.
 */
@TripInstanceCreatorService(InvocationTargetException.class)
public class InvocationTargetExceptionInstanceCreator implements TripInstanceCreator<InvocationTargetException> {

  /**
   * Creates the instance creator.
   */
  public InvocationTargetExceptionInstanceCreator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public InvocationTargetException create(Class<InvocationTargetException> clazz) {
    return new InvocationTargetException(null);
  }

  @Override
  public InvocationTargetException create(Class<InvocationTargetException> clazz,
                                          Map<TripComponent<InvocationTargetException, ?>, Object> values) {
    Throwable target = null;
    String message = null;
    StackTraceElement[] stackTrace = null;
    for (Map.Entry<TripComponent<InvocationTargetException, ?>, Object> entry : values.entrySet()) {
      switch (entry.getKey().getName()) {
        case "targetException":   // see InvocationTargetExceptionComponentProvider
          target = (Throwable) entry.getValue();
          break;
        case "message":
          message = (String) entry.getValue();
          break;
        case "stackTrace":
          stackTrace = (StackTraceElement[]) entry.getValue();
          break;
      }
    }
    InvocationTargetException exception = new InvocationTargetException(target, message);
    if (stackTrace != null) {
      exception.setStackTrace(stackTrace);
    }
    return exception;
  }

  @Override
  public boolean isCreationFromValuesSupported(Class<InvocationTargetException> clazz) {
    return true;
  }

}
