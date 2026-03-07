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

import org.tentackle.common.BMoney;
import org.tentackle.common.DMoney;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Trip type for {@link BigDecimal}.
 */
@TripTypeService(BigDecimal.class)
public final class BigDecimalTripType extends AbstractTripType<BigDecimal> {

  private static final char BIGDECIMAL_TYPE_CODE = 'B';
  private static final char DMONEY_TYPE_CODE = 'D';
  private static final char BMONEY_TYPE_CODE = 'M';

  /**
   * Creates the type.
   */
  public BigDecimalTripType() {
    super(BigDecimal.class);
  }

  @Override
  public boolean isFinal() {
    // BigDecimal is not final but cannot be treated as a regular object
    // since it's internal representation needs to be synced via unscaledValue().
    // If this type is extended by other types than BMoney or DMoney,
    // the application must provide a custom trip type.
    return true;
  }

  @Override
  public void write(BigDecimal value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.BIGDECIMAL);
      char typeCode;
      if (value instanceof DMoney) {
        typeCode = DMONEY_TYPE_CODE;
      }
      else if (value instanceof BMoney) {
        typeCode = BMONEY_TYPE_CODE;
      }
      else {
        typeCode = BIGDECIMAL_TYPE_CODE;
      }
      serializer.serializeChar(typeCode);
      byte[] bytes = toBytes(value);
      if (bytes == null) {
        // fits into a long integer
        serializer.serializeBoolean(true);
        serializer.serializeLong(value.scaleByPowerOfTen(value.scale()).longValue());
      }
      else {
        serializer.serializeBoolean(false);
        serializer.serializeInt(bytes.length);
        serializer.serialize(bytes, 0, bytes.length);
      }
      serializer.serializeInt(value.scale());
    }
  }

  @Override
  public BigDecimal read(TripStream stream, OpCode opCode) {
    BigDecimal value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.BIGDECIMAL) {
      char typeCode = deserializer.deserializeChar();
      if (deserializer.deserializeBoolean()) {
        value = BigDecimal.valueOf(deserializer.deserializeLong(), deserializer.deserializeInt());
      }
      else {
        byte[] bytes = new byte[deserializer.deserializeInt()];
        deserializer.deserialize(bytes, 0, bytes.length);
        value = new BigDecimal(new BigInteger(bytes), deserializer.deserializeInt());
      }
      if (typeCode == DMONEY_TYPE_CODE) {
        value = new DMoney(value);
      }
      else if (typeCode == BMONEY_TYPE_CODE) {
        value = new BMoney(value);
      }
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }


  /**
   * Returns the byte array representation of the given BigDecimal
   * if it does not fit into a long integer.
   *
   * @param value the big decimal value
   * @return the byte array representation, null if it fits into a long integer
   */
  private byte[] toBytes(BigDecimal value) {
    if (value.precision() >= 19) {
      BigInteger unscaled = value.unscaledValue();    // this syncs internally (unsafe stuff...)
      if (unscaled.bitLength() > 63) {
        return unscaled.toByteArray();
      }
      // <= 63 bits, fits into a long integer
    }
    return null;
  }

}
