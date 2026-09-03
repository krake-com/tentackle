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


interface StringTypeHolder {
  TripType<String> INSTANCE = TripFactory.getInstance().getType(String.class);
}


/**
 * Trip type for {@link String[]} array.
 */
@TripTypeService(String[].class)
public final class StringArrayTripType extends AbstractPredefinedArrayTripType<String[]> {

  /**
   * Creates the type.
   */
  public StringArrayTripType() {
    super(String[].class);
  }

  @Override
  public void write(String[] value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      TripType<String> stringTripType = StringTypeHolder.INSTANCE;
      if (writeArrayHeader(value, stream)) {
        int skip = 0;
        for (String stringValue : value) {
          if (stringValue != null) {
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
            stringTripType.write(stringValue, stream);
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
  public String[] read(TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    TripType<String> stringTripType = StringTypeHolder.INSTANCE;
    Deserializer deserializer = stream.getDeserializer();
    Reference<String[]> reference = receiveReference(stream, stream.receiveOpCode());
    if (reference.isObjectFollowing()) {
      int length = readArrayLength(deserializer, opCode);
      String[] result = new String[length];
      int count = 0;
      while (count < length) {
        opCode = stream.receiveOpCode();
        int skip = readSkip(deserializer, opCode);
        if (skip != NO_SKIP) {
          count += skip;
        }
        else {
          result[count++] = stringTripType.read(stream, opCode);
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
