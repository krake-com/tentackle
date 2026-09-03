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

package org.tentackle.trip.types;

import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;


interface LongTypeHolder {
  TripType<Long> INSTANCE = TripFactory.getInstance().getType(Long.class);
}


/**
 * Trip type for {@link Long[]} array.
 */
@TripTypeService(Long[].class)
public final class LongArrayTripType extends AbstractPredefinedArrayTripType<Long[]> {

  /**
   * Creates the type.
   */
  public LongArrayTripType() {
    super(Long[].class);
  }

  @Override
  public void write(Long[] value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      TripType<Long> longTripType = LongTypeHolder.INSTANCE;
      if (writeArrayHeader(value, stream)) {
        int skip = 0;
        for (Long longValue : value) {
          if (longValue != null) {
            if (skip > 0) {
              if (skip == 1) {
                serializer.serializeOpCode(DefaultOpCode.NULL);
              }
              else if (skip == 2) {
                serializer.serializeOpCode(DefaultOpCode.NULL);
                serializer.serializeOpCode(DefaultOpCode.NULL);
              }
              else {
                writeSkip(skip, serializer);
              }
              skip = 0;
            }
            longTripType.write(longValue, stream);
          }
          else {
            skip++;
          }
        }
        if (skip > 0) {
          writeSkip(skip, serializer);
        }
      }
    }
  }

  @Override
  public Long[] read(TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    TripType<Long> longTripType = LongTypeHolder.INSTANCE;
    Deserializer deserializer = stream.getDeserializer();
    Reference<Long[]> reference = receiveReference(stream, stream.receiveOpCode());
    if (reference.isObjectFollowing()) {
      int length = readArrayLength(deserializer, opCode);
      Long[] result = new Long[length];
      int count = 0;
      while (count < length) {
        opCode = stream.receiveOpCode();
        int skip = readSkip(deserializer, opCode);
        if (skip != NO_SKIP) {
          count += skip;
        }
        else {
          result[count++] = longTripType.read(stream, opCode);
        }
      }
      stream.addReference(reference, result, false);
      reference.markReceived();
      return result;
    }
    else {
      return reference.getObject();
    }
  }

}
