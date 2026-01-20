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


interface StackTraceElementTypeHolder {
  TripType<String> STRING_TYPE = TripFactory.getInstance().getType(String.class);
  TripType<Integer> INTEGER_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
}


/**
 * Trip type for {@link StackTraceElement}.
 */
@TripTypeService(StackTraceElement.class)
public final class StackTraceElementTripType extends AbstractTripType<StackTraceElement> {

  /**
   * Creates the type.
   */
  public StackTraceElementTripType() {
    super(StackTraceElement.class);
  }

  @Override
  public void write(StackTraceElement value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.STACKTRACE_ELEMENT);
      StackTraceElementTypeHolder.STRING_TYPE.write(value.getClassLoaderName(), stream);
      StackTraceElementTypeHolder.STRING_TYPE.write(value.getModuleName(), stream);
      StackTraceElementTypeHolder.STRING_TYPE.write(value.getModuleVersion(), stream);
      StackTraceElementTypeHolder.STRING_TYPE.write(value.getClassName(), stream);
      StackTraceElementTypeHolder.STRING_TYPE.write(value.getMethodName(), stream);
      StackTraceElementTypeHolder.STRING_TYPE.write(value.getFileName(), stream);
      StackTraceElementTypeHolder.INTEGER_TYPE.write(value.getLineNumber(), stream);
    }
  }

  @Override
  public StackTraceElement read(TripStream stream, OpCode opCode) {
    StackTraceElement value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.STACKTRACE_ELEMENT) {
      value = new StackTraceElement(StackTraceElementTypeHolder.STRING_TYPE.read(stream, deserializer.deserializeOpCode()),
                                    StackTraceElementTypeHolder.STRING_TYPE.read(stream, deserializer.deserializeOpCode()),
                                    StackTraceElementTypeHolder.STRING_TYPE.read(stream, deserializer.deserializeOpCode()),
                                    StackTraceElementTypeHolder.STRING_TYPE.read(stream, deserializer.deserializeOpCode()),
                                    StackTraceElementTypeHolder.STRING_TYPE.read(stream, deserializer.deserializeOpCode()),
                                    StackTraceElementTypeHolder.STRING_TYPE.read(stream, deserializer.deserializeOpCode()),
                                    StackTraceElementTypeHolder.INTEGER_TYPE.read(stream, deserializer.deserializeOpCode()));
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}
