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

import org.tentackle.common.I18NText;
import org.tentackle.common.ParameterString;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.text.ParseException;


interface I18NTextTypeHolder {
  TripType<String> STRING_INSTANCE = TripFactory.getInstance().getType(String.class);
}


/**
 * Trip type for {@link I18NText}.
 */
@TripTypeService(I18NText.class)
public final class I18NTextTripType extends AbstractTripType<I18NText> {

  /**
   * Creates the type.
   */
  public I18NTextTripType() {
    super(I18NText.class);
  }

  @Override
  public boolean isFinal() {
    // I18NText isn't final.
    // In case the application extends it with additional fields,
    // the application must provide a custom trip type.
    return true;
  }

  @Override
  public void write(I18NText value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.I18NTEXT);
      I18NTextTypeHolder.STRING_INSTANCE.write(value.toParameterString().toString(), stream);
    }
  }

  @Override
  public I18NText read(TripStream stream, OpCode opCode) {
    I18NText value;
    if (opCode == DefaultOpCode.I18NTEXT) {
      String text = I18NTextTypeHolder.STRING_INSTANCE.read(stream, stream.receiveOpCode());
      try {
        value = new I18NText(new ParameterString(text));
      }
      catch (ParseException e) {
        throw new TripRuntimeException("cannot parse I18NText", e);
      }
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}
