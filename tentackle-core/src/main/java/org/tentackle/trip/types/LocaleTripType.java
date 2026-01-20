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
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.util.Locale;


interface LocaleTypeHolder {
  TripType<String> STRING_INSTANCE = TripFactory.getInstance().getType(String.class);
}


/**
 * Trip type for {@link Locale}.
 */
@TripTypeService(Locale.class)
public final class LocaleTripType extends AbstractTripType<Locale> {

  /**
   * Creates the type.
   */
  public LocaleTripType() {
    super(Locale.class);
  }

  @Override
  public void write(Locale value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.LOCALE);
      LocaleTypeHolder.STRING_INSTANCE.write(value.getLanguage() + "_" +
                                             value.getCountry() + "_" +
                                             value.getVariant(),
                                             stream);
    }
  }

  @Override
  public Locale read(TripStream stream, OpCode opCode) {
    Locale value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.LOCALE) {
      String[] localeParts = LocaleTypeHolder.STRING_INSTANCE.read(stream, deserializer.deserializeOpCode())
                                                             .split("_");
      value = switch (localeParts.length) {
        case 0 -> Locale.ROOT;
        case 1 -> Locale.of(localeParts[0]);
        case 2 -> Locale.of(localeParts[0], localeParts[1]);
        default -> Locale.of(localeParts[0], localeParts[1], localeParts[2]);
      };
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}
