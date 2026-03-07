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

package org.tentackle.trip.remote;

import org.tentackle.trip.TripDeduplicatable;
import org.tentackle.trip.TripReferencable;

/**
 * The remote call's response.
 *
 * @param methodIndex the method's index to be used for all following calls
 * @param result the return value
 * @param throwable the throwable, if there was any remote exception, null for success
 */
@TripReferencable(TripReferencable.NEVER)
@TripDeduplicatable(TripDeduplicatable.NEVER)
public record InitialRemoteResponse(int methodIndex, Object result, Throwable throwable)
       implements RemoteResponse {

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("initial remote response: methodIndex=").append(methodIndex);
    if (throwable != null) {
      buf.append(", exception=").append(throwable);
    }
    else {
      buf.append(", result=");
      if (result == null) {
        buf.append("<null>");
      }
      else {
        buf.append(result.getClass().getName());
      }
    }
    return buf.toString();
  }

}
