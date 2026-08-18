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
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

/**
 * Sends object reference as a compact long value.
 * <p>
 * References must be sent immediately following the {@link OpCode} of the
 * corresponding {@link org.tentackle.trip.TripType} and are only allowed
 * for dictionary-based types. The next OpCode for the reader of the stream then
 * sees either a reference or a dictionary opcode, but not the data. That's why OpCodes
 * with a fixed type cannot have references (an exception is {@link StringTripType}
 * which provides its own codes whether with or without reference).
 */
@TripTypeService(Reference.class)
public final class ReferenceTripType extends AbstractTripType<Reference<?>> {

  /**
   * Creates the type.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public ReferenceTripType() {
    super((Class) Reference.class);
  }

  @Override
  public void write(Reference<?> reference, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    // reference IDs are always >= 0
    int referenceId = reference.getReferenceId();
    if (referenceId <= TripConstants.UNSIGNED_SHORT_MAX) {
      if (referenceId <= TripConstants.UNSIGNED_BYTE_MAX) {
        serializer.serializeOpCode(reference.isSent() ? DefaultOpCode.REFERENCE1_SENT : DefaultOpCode.REFERENCE1);
        serializer.serializeUnsignedAsByte(referenceId);
      }
      else {
        serializer.serializeOpCode(reference.isSent() ? DefaultOpCode.REFERENCE2_SENT : DefaultOpCode.REFERENCE2);
        serializer.serializeUnsignedAsShort(referenceId);
      }
    }
    else {
      if (referenceId <= TripConstants.UNSIGNED_INT3_MAX) {
        serializer.serializeOpCode(reference.isSent() ? DefaultOpCode.REFERENCE3_SENT : DefaultOpCode.REFERENCE3);
        serializer.serializeInt3(referenceId);
      }
      else {
        serializer.serializeOpCode(reference.isSent() ? DefaultOpCode.REFERENCE_SENT : DefaultOpCode.REFERENCE);
        serializer.serializeInt(referenceId);
      }
    }
  }

  @Override
  public Reference<?> read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    int code = opCode.getCode();
    if (code < DefaultOpCode.REFERENCE.getCode() || code > DefaultOpCode.REFERENCE3_SENT.getCode()) {
      throw new TripRuntimeException("invalid reference opcode: " + opCode);
    }
    int referenceId = switch (code & 0x3) {
      case 0 -> deserializer.deserializeInt();
      case 1 -> deserializer.deserializeByteAsUnsigned();
      case 2 -> deserializer.deserializeShortAsUnsigned();
      case 3 -> deserializer.deserializeInt3AsUnsigned();
      default -> throw new TripRuntimeException("what??" + opCode);
    };
    if (referenceId < 0) {
      // only the 4-byte form can be negative, and only from a corrupted stream
      throw new TripRuntimeException("negative reference id " + referenceId + " for " + opCode);
    }
    return new Reference<>(referenceId, (code & 0x4) != 0, false);
  }

}
