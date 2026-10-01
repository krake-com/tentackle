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


interface IntegerTypeHolder {
  TripType<Integer> INSTANCE = TripFactory.getInstance().getType(Integer.class);
}


/**
 * Trip type for {@code Integer[]} array.
 */
@TripTypeService(Integer[].class)
public final class IntegerArrayTripType extends AbstractPredefinedArrayTripType<Integer[]> {

  /**
   * Creates the type.
   */
  public IntegerArrayTripType() {
    super(Integer[].class);
  }

  @Override
  public void write(Integer[] value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      TripType<Integer> integerTripType = IntegerTypeHolder.INSTANCE;
      if (writeArrayHeader(value, stream)) {
        int skip = 0;
        for (Integer integerValue : value) {
          if (integerValue != null) {
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
            integerTripType.write(integerValue, stream);
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
  public Integer[] read(TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    TripType<Integer> integerTripType = IntegerTypeHolder.INSTANCE;
    Deserializer deserializer = stream.getDeserializer();
    Reference<Integer[]> reference = receiveReference(stream, stream.receiveOpCode());
    if (reference.isObjectFollowing()) {
      int length = readArrayLength(deserializer, opCode);
      Integer[] result = new Integer[length];
      int count = 0;
      while (count < length) {
        opCode = stream.receiveOpCode();
        int skip = readSkip(deserializer, opCode);
        if (skip != NO_SKIP) {
          count += skip;
        }
        else {
          result[count++] = integerTripType.read(stream, opCode);
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
