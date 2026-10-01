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

import java.util.UUID;

/**
 * The remote call.<br>
 * Used when the method is called the first time on the remote object.
 * <p>
 * Since this record is never sent back from the server to the client,
 * its {@link org.tentackle.trip.DictionaryEntry} will remain {@code client-only}
 * forever in both dictionaries.
 *
 * @param remoteId the ID of the remote object
 * @param methodName the name of the method to call
 * @param parameterTypes the parameter types of the method
 * @param parameters the parameters of the method
 */
@TripReferencable(TripReferencable.NEVER)
@TripDeduplicatable(TripDeduplicatable.NEVER)
public record InitialRemoteCall(UUID remoteId, String methodName, Class<?>[] parameterTypes, Object[] parameters)
       implements RemoteCall {

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("initial remote call: id=").append(remoteId).append(", method=").append(methodName)
       .append(", paramTypes=[");
    boolean needComma = false;
    if (parameterTypes != null) {
      for (Class<?> paramType : parameterTypes) {
        if (needComma) {
          buf.append(", ");
        }
        else {
          needComma = true;
        }
        buf.append(paramType.getName());
      }
    }
    needComma = false;
    buf.append("], params=[");
    if (parameters != null) {
      for (Object param : parameters) {
        if (needComma) {
          buf.append(", ");
        }
        else {
          needComma = true;
        }
        if (param == null) {
          buf.append("<null>");
        }
        else {
          buf.append(param.getClass().getName());
        }
      }
    }
    buf.append("]");
    return buf.toString();
  }

}
