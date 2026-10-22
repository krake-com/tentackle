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
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.nio.charset.StandardCharsets;

/**
 * String type.
 * <p>
 * Although a fixed type, it supports deduplication via references.
 * That's why the OpCodes are different depending on whether a reference follows or not.
 * <p>
 * The class is not final to allow application-specific replacements overriding
 * {@link #isDeduplicateable(String)}.
 */
@TripTypeService(String.class)
public class StringTripType extends AbstractTripType<String> {

  /**
   * Creates the type.
   */
  public StringTripType() {
    super(String.class);
  }

  @Override
  public boolean isDeduplicateable(String value) {
    // The shortest non-empty, non-referenced string is 3 bytes long (OpCode, len, 1 char).
    // The shortest referenced string is 3 bytes long (OpCode, REF-code, ordinal).
    // So, theoretically, strings with 4 or more bytes require more bytes
    // than their reference. However, deduplication takes cpu time, references take memory,
    // and more than 256 references take even more bytes. So starting at a length
    // of 6 seems a reasonable value.
    return deduplicationEnabled && value != null && value.length() > 5;
  }

  @Override
  public void write(String value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      if (value.isEmpty()) {
        serializer.serializeOpCode(DefaultOpCode.STRING_EMPTY);
      }
      else {
        Reference<String> reference;
        boolean sendString;
        if (isDeduplicateable(value)) {
          reference = stream.getReference(value, true);
          sendString = !reference.isSent();
        }
        else {
          reference = null;
          sendString = true;
        }
        if (sendString) {
          byte[] data = value.getBytes(StandardCharsets.UTF_8);
          int len = data.length;
          if (len < 256) {
            if (reference != null) {
              serializer.serializeOpCode(DefaultOpCode.STRING1_REF);
              reference.send(stream);
            }
            else {
              serializer.serializeOpCode(DefaultOpCode.STRING1);
            }
            serializer.serializeUnsignedAsByte(len);
          }
          else if (len < 65536) {
            if (reference != null) {
              serializer.serializeOpCode(DefaultOpCode.STRING2_REF);
              reference.send(stream);
            }
            else {
              serializer.serializeOpCode(DefaultOpCode.STRING2);
            }
            serializer.serializeUnsignedAsShort(len);
          }
          else {
            if (reference != null) {
              serializer.serializeOpCode(DefaultOpCode.STRING_REF);
              reference.send(stream);
            }
            else {
              serializer.serializeOpCode(DefaultOpCode.STRING);
            }
            serializer.serializeInt(len);
          }

          serializer.serialize(data, 0, len);
        }
        else {
          // send only the reference
          serializer.serializeOpCode(DefaultOpCode.STRING_REF);
          reference.send(stream);
        }
      }
    }
  }

  @Override
  public String read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();

    OpCode orgCode = opCode;

    if (opCode == DefaultOpCode.STRING1_REF) {
      opCode = stream.receiveOpCode();
      orgCode = DefaultOpCode.STRING1;
    }
    else if (opCode == DefaultOpCode.STRING2_REF) {
      opCode = stream.receiveOpCode();
      orgCode = DefaultOpCode.STRING2;
    }
    else if (opCode == DefaultOpCode.STRING_REF) {
      opCode = stream.receiveOpCode();
      orgCode = DefaultOpCode.STRING;
    }

    String value = null;
    boolean readContent = true;
    Reference<String> reference = null;
    if (opCode.isReference()) {
      reference = receiveReference(stream, opCode);
      readContent = reference.isObjectFollowing();
      if (!readContent) {
        value = reference.getObject();
        if (value == null) {
          throw new TripRuntimeException("reference " + reference + " has no String object");
        }
      }
    }

    if (readContent) {
      // narrowed to DefaultOpCode before switching, see AbstractPredefinedArrayTripType#readSkip
      int len = orgCode instanceof DefaultOpCode code ?
                  switch (code) {
                    case STRING -> deserializer.deserializeInt();
                    case STRING2 -> deserializer.deserializeShortAsUnsigned();
                    case STRING1 -> deserializer.deserializeByteAsUnsigned();
                    case STRING_EMPTY -> 0;
                    default -> -1;
                  } : -1;
      if (len == 0) {
        value = "";
      }
      else if (len > 0) {
        value = deserializer.deserializeUtf8(len);
        if (reference != null) {
          stream.addReference(reference, value, isDeduplicateable(value));
          reference.markReceived();
        }
      }
      else {
        return super.read(stream, orgCode);
      }
    }

    return value;
  }

}
