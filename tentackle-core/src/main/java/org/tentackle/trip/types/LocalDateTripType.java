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
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.time.LocalDate;

/**
 * Trip type for {@link LocalDate}.
 */
@TripTypeService(LocalDate.class)
public final class LocalDateTripType extends AbstractTripType<LocalDate> {

  /**
   * Creates the type.
   */
  public LocalDateTripType() {
    super(LocalDate.class);
  }

  @Override
  public void write(LocalDate value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.LOCALDATE);
      serializer.serializeInt(value.getYear());
      serializer.serializeUnsignedAsByte(value.getMonthValue());
      serializer.serializeUnsignedAsByte(value.getDayOfMonth());
    }
  }

  @Override
  public LocalDate read(TripStream stream, OpCode opCode) {
    LocalDate value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.LOCALDATE) {
      value = LocalDate.of(deserializer.deserializeInt(),
                           deserializer.deserializeByteAsUnsigned(),
                           deserializer.deserializeByteAsUnsigned());
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}
