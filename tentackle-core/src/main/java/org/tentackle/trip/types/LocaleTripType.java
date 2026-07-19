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
 * <p>
 * Locales that survive the round trip through a BCP 47 language tag are serialized
 * as their tag, which preserves script and extensions. All other locales (legacy
 * variants that are not representable as a tag) are serialized as their
 * language, country and variant parts.
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
      String languageTag = value.toLanguageTag();
      if (Locale.forLanguageTag(languageTag).equals(value)) {
        serializer.serializeBoolean(true);
        LocaleTypeHolder.STRING_INSTANCE.write(languageTag, stream);
      }
      else {
        serializer.serializeBoolean(false);
        LocaleTypeHolder.STRING_INSTANCE.write(value.getLanguage(), stream);
        LocaleTypeHolder.STRING_INSTANCE.write(value.getCountry(), stream);
        LocaleTypeHolder.STRING_INSTANCE.write(value.getVariant(), stream);
      }
    }
  }

  @Override
  public Locale read(TripStream stream, OpCode opCode) {
    Locale value;
    if (opCode == DefaultOpCode.LOCALE) {
      if (stream.getDeserializer().deserializeBoolean()) {
        value = Locale.forLanguageTag(LocaleTypeHolder.STRING_INSTANCE.read(stream, stream.receiveOpCode()));
      }
      else {
        String language = LocaleTypeHolder.STRING_INSTANCE.read(stream, stream.receiveOpCode());
        String country = LocaleTypeHolder.STRING_INSTANCE.read(stream, stream.receiveOpCode());
        String variant = LocaleTypeHolder.STRING_INSTANCE.read(stream, stream.receiveOpCode());
        value = Locale.of(language, country, variant);
      }
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}
