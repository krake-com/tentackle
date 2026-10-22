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

package org.tentackle.pdo.trip;

import org.tentackle.pdo.LockException;
import org.tentackle.pdo.TokenLockInfo;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripInstanceCreator;
import org.tentackle.trip.TripInstanceCreatorService;

import java.util.Map;

/**
 * Creates {@link LockException}s when deserialized by TRIP.
 * <p>
 * A {@link LockException} has no no-arg constructor, and its final fields and message are derived
 * from either the {@link TokenLockInfo} or the name of the already locked context.
 * Hence, it is created from the transferred component values.
 * The session is not transferred and remains null.
 *
 * @author harald
 */
@TripInstanceCreatorService(LockException.class)
public class LockExceptionInstanceCreator implements TripInstanceCreator<LockException> {

  /**
   * Creates the instance creator.
   */
  public LockExceptionInstanceCreator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public LockException create(Class<LockException> clazz) {
    return new LockException(null, "");
  }

  @Override
  public LockException create(Class<LockException> clazz, Map<TripComponent<LockException, ?>, Object> values) {
    TokenLockInfo tokenLockInfo = null;
    String contextName = null;
    StackTraceElement[] stackTrace = null;
    for (Map.Entry<TripComponent<LockException, ?>, Object> entry : values.entrySet()) {
      switch (entry.getKey().getName()) {
        case "tokenLockInfo":
          tokenLockInfo = (TokenLockInfo) entry.getValue();
          break;
        case "contextName":
          contextName = (String) entry.getValue();
          break;
        case "stackTrace":
          stackTrace = (StackTraceElement[]) entry.getValue();
          break;
      }
    }
    LockException exception;
    if (tokenLockInfo != null) {
      exception = new LockException(null, tokenLockInfo);
    }
    else {
      // contextName cannot be null, but just in case
      exception = new LockException(null, contextName == null ? "" : contextName);
    }
    if (stackTrace != null) {
      exception.setStackTrace(stackTrace);
    }
    return exception;
  }

  @Override
  public boolean isCreationFromValuesSupported(Class<LockException> clazz) {
    return true;
  }

}
