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


interface FloatPrimitiveTypeHolder {
  TripType<Float> INSTANCE = TripFactory.getInstance().getType(Float.TYPE);
}


/**
 * Trip type for {@code float[]} array.
 */
@TripTypeService(float[].class)
public final class FloatPrimitiveArrayTripType extends AbstractPredefinedArrayTripType<float[]> {

  /**
   * Creates the type.
   */
  public FloatPrimitiveArrayTripType() {
    super(float[].class);
  }

  @Override
  public void write(float[] value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      TripType<Float> floatTripType = FloatPrimitiveTypeHolder.INSTANCE;
      if (writeArrayHeader(value, stream)) {
        int skip = 0;
        for (float floatValue : value) {
          if (floatValue != 0.0f) {
            if (skip > 0) {
              writeSkip(skip, serializer);    // skip 1 and short1 0 occupy 2 bytes both the same
              skip = 0;
            }
            floatTripType.write(floatValue, stream);
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
  public float[] read(TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    TripType<Float> floatTripType = FloatPrimitiveTypeHolder.INSTANCE;
    Deserializer deserializer = stream.getDeserializer();
    Reference<float[]> reference = receiveReference(stream, stream.receiveOpCode());
    if (reference.isObjectFollowing()) {
      int length = readArrayLength(deserializer, opCode);
      float[] result = new float[length];
      int count = 0;
      while (count < length) {
        opCode = stream.receiveOpCode();
        int skip = readSkip(deserializer, opCode);
        if (skip != NO_SKIP) {
          count += skip;
        }
        else {
          result[count++] = floatTripType.read(stream, opCode);
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
