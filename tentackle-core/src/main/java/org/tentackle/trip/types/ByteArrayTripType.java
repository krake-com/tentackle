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


interface ByteTypeHolder {
  TripType<Byte> INSTANCE = TripFactory.getInstance().getType(Byte.class);
}


/**
 * Trip type for {@code Byte[]} array.
 */
@TripTypeService(Byte[].class)
public final class ByteArrayTripType extends AbstractPredefinedArrayTripType<Byte[]> {

  /**
   * Creates the type.
   */
  public ByteArrayTripType() {
    super(Byte[].class);
  }

  @Override
  public void write(Byte[] value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      TripType<Byte> byteTripType = ByteTypeHolder.INSTANCE;
      if (writeArrayHeader(value, stream)) {
        int skip = 0;
        int ndx = 0;
        int blockStart = -1;
        for (Byte byteValue : value) {
          int blockLen = blockStart == -1 ? 0 : ndx - blockStart;
          if (byteValue != null) {
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
                byteTripType.write(value[blockStart], stream);
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
            byteTripType.write(value[blockStart], stream);
          }
        }
        else if (skip > 0) {
          writeSkip(skip, serializer);
        }
      }
    }
  }

  @Override
  public Byte[] read(TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    TripType<Byte> byteTripType = ByteTypeHolder.INSTANCE;
    Deserializer deserializer = stream.getDeserializer();
    Reference<Byte[]> reference = receiveReference(stream, stream.receiveOpCode());
    if (reference.isObjectFollowing()) {
      int length = readArrayLength(deserializer, opCode);
      Byte[] result = new Byte[length];
      int count = 0;
      while (count < length) {
        opCode = stream.receiveOpCode();
        int skip = readSkip(deserializer, opCode);
        if (skip != NO_SKIP) {
          count += skip;
        }
        else if (opCode == DefaultOpCode.BYTEN) {
          int blockLen = deserializer.deserializeByteAsUnsigned();
          while (blockLen-- > 0) {
            result[count++] = deserializer.deserializeByte();
          }
        }
        else {
          result[count++] = byteTripType.read(stream, opCode);
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


  private void flushBlock(Serializer serializer, Byte[] value, int blockStart, int blockLen) {
    serializer.serializeOpCode(DefaultOpCode.BYTEN);
    serializer.serializeUnsignedAsByte(blockLen);
    while (blockLen-- > 0) {
      serializer.serializeByte(value[blockStart++]);
    }
  }

}
