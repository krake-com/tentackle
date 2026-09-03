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


interface BooleanPrimitiveTypeHolder {
  TripType<Boolean> INSTANCE = TripFactory.getInstance().getType(Boolean.TYPE);
}


/**
 * Trip type for {@code boolean[]} array.
 */
@TripTypeService(boolean[].class)
public final class BooleanPrimitiveArrayTripType extends AbstractPredefinedArrayTripType<boolean[]> {

  /**
   * Creates the type.
   */
  public BooleanPrimitiveArrayTripType() {
    super(boolean[].class);
  }

  @Override
  public void write(boolean[] value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      TripType<Boolean> booleanTripType = BooleanPrimitiveTypeHolder.INSTANCE;
      if (writeArrayHeader(value, stream)) {
        int skip = 0;
        int ndx = 0;
        int blockStart = -1;
        for (boolean booleanValue : value) {
          int blockLen = blockStart == -1 ? 0 : ndx - blockStart;
          if (booleanValue) {
            if (skip > 0) {
              if (skip == 1) {
                blockStart = ndx - 1;
              }
              else {
                writeSkip(skip, serializer);
              }
              skip = 0;
            }
            if (blockStart == -1) {
              blockStart = ndx;
            }
            else if (blockLen == 255) {
              flushBlock(serializer, value, blockStart, blockLen);
              blockStart = ndx;
            }
          }
          else {
            skip++;
            if (blockStart != -1) {
              if (blockLen > 1) {
                flushBlock(serializer, value, blockStart, blockLen);
              }
              else {
                booleanTripType.write(value[blockStart], stream);
              }
              blockStart = -1;
            }
          }
          ndx++;
        }
        if (blockStart != -1) {
          int blockLen = ndx - blockStart;
          if (blockLen > 1) {
            flushBlock(serializer, value, blockStart, blockLen);
          }
          else {
            booleanTripType.write(value[blockStart], stream);
          }
        }
        else if (skip > 0) {
          writeSkip(skip, serializer);
        }
      }
    }
  }

  @Override
  public boolean[] read(TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    TripType<Boolean> booleanTripType = BooleanPrimitiveTypeHolder.INSTANCE;
    Deserializer deserializer = stream.getDeserializer();
    Reference<boolean[]> reference = receiveReference(stream, stream.receiveOpCode());
    if (reference.isObjectFollowing()) {
      int length = readArrayLength(deserializer, opCode);
      boolean[] result = new boolean[length];
      int count = 0;
      while (count < length) {
        opCode = stream.receiveOpCode();
        int skip = readSkip(deserializer, opCode);
        if (skip != NO_SKIP) {
          count += skip;
        }
        else if (opCode == DefaultOpCode.BOOLN) {
          int blockLen = deserializer.deserializeByteAsUnsigned();
          while (blockLen-- > 0) {
            result[count++] = deserializer.deserializeBoolean();
          }
        }
        else {
          result[count++] = booleanTripType.read(stream, opCode);
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


  private void flushBlock(Serializer serializer, boolean[] value, int blockStart, int blockLen) {
    serializer.serializeOpCode(DefaultOpCode.BOOLN);
    serializer.serializeUnsignedAsByte(blockLen);
    while (blockLen-- > 0) {
      serializer.serializeBoolean(value[blockStart++]);
    }
  }

}
