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


interface CharacterPrimitiveTypeHolder {
  TripType<Character> INSTANCE = TripFactory.getInstance().getType(Character.TYPE);
}


/**
 * Trip type for {@code char[]} array.
 */
@TripTypeService(char[].class)
public final class CharacterPrimitiveArrayTripType extends AbstractPredefinedArrayTripType<char[]> {

  /**
   * Creates the type.
   */
  public CharacterPrimitiveArrayTripType() {
    super(char[].class);
  }

  @Override
  public void write(char[] value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      TripType<Character> charTripType = CharacterPrimitiveTypeHolder.INSTANCE;
      if (writeArrayHeader(value, stream)) {
        int length = value.length;
        int ndx = 0;
        while (ndx < length) {
          if (value[ndx] == 0) {
            int end = ndx + 1;
            while (end < length && value[end] == 0) {
              end++;
            }
            writeSkip(end - ndx, serializer);
            ndx = end;
          }
          else if (value[ndx] >>> 8 != 0) {
            charTripType.write(value[ndx], stream);
            ndx++;
          }
          else {
            // the block always ends behind a non-zero value that fits into the block,
            // short zero runs in between are kept
            int blockEnd = ndx + 1;
            int maxEnd = Math.min(length, ndx + MAX_BLOCK_LENGTH);
            int pos = blockEnd;
            while (pos < maxEnd) {
              char v = value[pos];
              if (v != 0) {
                if (v >>> 8 != 0) {
                  break;    // sent as a single value
                }
                blockEnd = ++pos;
              }
              else if (pos - blockEnd < MAX_INNER_ZEROS) {
                pos++;
              }
              else {
                break;
              }
            }
            int blockLen = blockEnd - ndx;
            if (blockLen > 1) {
              flushBlock(serializer, value, ndx, blockLen);
            }
            else {
              charTripType.write(value[ndx], stream);
            }
            ndx = blockEnd;
          }
        }
      }
    }
  }

  @Override
  public char[] read(TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    TripType<Character> charTripType = CharacterPrimitiveTypeHolder.INSTANCE;
    Deserializer deserializer = stream.getDeserializer();
    Reference<char[]> reference = receiveReference(stream, stream.receiveOpCode());
    if (reference.isObjectFollowing()) {
      int length = readArrayLength(deserializer, opCode);
      char[] result = new char[length];
      int count = 0;
      while (count < length) {
        opCode = stream.receiveOpCode();
        int skip = readSkip(deserializer, opCode);
        if (skip != NO_SKIP) {
          count += skip;
        }
        else if (opCode == DefaultOpCode.CHARN) {
          int blockLen = deserializer.deserializeByteAsUnsigned();
          while (blockLen-- > 0) {
            result[count++] = (char) deserializer.deserializeByteAsUnsigned();
          }
        }
        else {
          result[count++] = charTripType.read(stream, opCode);
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


  private void flushBlock(Serializer serializer, char[] value, int blockStart, int blockLen) {
    serializer.serializeOpCode(DefaultOpCode.CHARN);
    serializer.serializeUnsignedAsByte(blockLen);
    while (blockLen-- > 0) {
      serializer.serializeUnsignedAsByte(value[blockStart++]);
    }
  }

}
