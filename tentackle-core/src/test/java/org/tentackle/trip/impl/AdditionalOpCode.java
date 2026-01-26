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

package org.tentackle.trip.impl;

import org.tentackle.trip.OpCode;
import org.tentackle.trip.PredefinedArrayType;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

public enum AdditionalOpCode implements OpCode {

  FEE(0x80),
  FIE(0x81),
  FOO(0x82),
  FOE(0x83);

  private final int code;                              // unique operation code

  AdditionalOpCode(int code) {
    this.code = code;
  }

  @Override
  public int getCode() {
    return code;
  }

  @Override
  public Integer toExternal() {
    return code;
  }

  @Override
  public boolean isReference() {
    return false;
  }

  @Override
  public boolean isOrdinal() {
    return false;
  }

  @Override
  public boolean isDictionaryBased() {
    return false;
  }

  @Override
  public boolean isPredefinedArray() {
    return false;
  }

  @Override
  public TripType<?> getPredefinedArrayType(PredefinedArrayType arrayType) {
    return null;
  }

  @Override
  public boolean isFixedType() {
    return false;
  }

  @Override
  public boolean isError() {
    return false;
  }

  @Override
  public TripType<?> getFixedType() {
    throw new TripRuntimeException("not a fixed type");
  }

  public static AdditionalOpCode valueOf(int code) {
    AdditionalOpCode opCode = CODES[code];
    if (opCode == null) {
      throw new TripRuntimeException("invalid op code: " + String.format("0x%02X", code));
    }
    return opCode;
  }


  private static final AdditionalOpCode[] CODES = createArray();

  private static AdditionalOpCode[] createArray() {
    AdditionalOpCode[] codes = new AdditionalOpCode[256];
    for (AdditionalOpCode opCode : values()) {
      codes[opCode.code] = opCode;
    }
    return codes;
  }

}
