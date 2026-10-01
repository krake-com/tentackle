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
 * The indexed remote call.<br>
 * Used when the method's index is known, i.e. after the first invocation of the method
 * (see {@link InitialRemoteResponse#methodIndex()}).
 * <p>
 * Since this record is never sent back from the server to the client,
 * its {@link org.tentackle.trip.DictionaryEntry} will remain {@code client-only}
 * forever in both dictionaries.
 *
 * @param remoteId the ID of the remote object
 * @param methodIndex the index of the method to call
 * @param parameters the parameters of the method
 */
@TripReferencable(TripReferencable.NEVER)
@TripDeduplicatable(TripDeduplicatable.NEVER)
public record IdentifiedRemoteCall(UUID remoteId, int methodIndex, Object[] parameters)
       implements RemoteCall {

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("identified remote call: id=").append(remoteId).append(", methodIndex=").append(methodIndex);
    boolean needComma = false;
    buf.append(", params=[");
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
