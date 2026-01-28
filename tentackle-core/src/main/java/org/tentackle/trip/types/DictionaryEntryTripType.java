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

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.DictionaryEntryComponent;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.PredefinedArray;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;


interface DictionaryEntryTypeHolder {
  TripType<Boolean> BOOLEAN_INSTANCE = TripFactory.getInstance().getType(Boolean.TYPE);
  TripType<String> STRING_INSTANCE = TripFactory.getInstance().getType(String.class);
  TripType<String[]> STRING_ARRAY_INSTANCE = TripFactory.getInstance().getType(String[].class);

  // always set, but null in case the application replaces StringArrayTripType
  // by something else but a PredefinedArray.
  @SuppressWarnings("unchecked")
  PredefinedArray<String[]> PREDEFINED_STRING_ARRAY =
    STRING_ARRAY_INSTANCE instanceof PredefinedArray ?
      (PredefinedArray<String[]>) STRING_ARRAY_INSTANCE : null;
}


/**
 * Trip type for {@link DictionaryEntry}.
 */
@TripTypeService(DictionaryEntry.class)
public final class DictionaryEntryTripType extends AbstractTripType<DictionaryEntry> {

  /**
   * Creates the type.
   */
  public DictionaryEntryTripType() {
    super(DictionaryEntry.class);
  }

  @Override
  public void write(DictionaryEntry value, TripStream stream) {
    stream.getSerializer().serializeOpCode(DefaultOpCode.DICTIONARY);
    stream.writeComponentOrdinal(value.getOrdinal());
    DictionaryEntryTypeHolder.STRING_INSTANCE.write(value.getClassName(), stream);
    DictionaryEntryTypeHolder.BOOLEAN_INSTANCE.write(value.isProxy(), stream);
    String[] componentNames = new String[value.getComponents().length];
    String[] componentTypes = new String[componentNames.length];
    int i = 0;
    for (DictionaryEntryComponent component : value.getComponents()) {
      componentNames[i] = component.getComponentName();
      componentTypes[i++] = ReflectionHelper.getSerializableClassName(component.getType().getObjectClass());
    }
    DictionaryEntryTypeHolder.STRING_ARRAY_INSTANCE.write(componentNames, stream);
    DictionaryEntryTypeHolder.STRING_ARRAY_INSTANCE.write(componentTypes, stream);
  }

  @Override
  public DictionaryEntry read(TripStream stream, OpCode opCode) {
    if (opCode != DefaultOpCode.DICTIONARY) {
      throw new TripRuntimeException("unexpected opCode: " + opCode);
    }
    Dictionary dictionary = stream.getDictionary();
    int ordinal = stream.readComponentOrdinal(stream.receiveOpCode());
    String className = DictionaryEntryTypeHolder.STRING_INSTANCE.read(stream, stream.receiveOpCode());
    boolean isProxy = DictionaryEntryTypeHolder.BOOLEAN_INSTANCE.read(stream, stream.receiveOpCode());
    opCode = stream.receiveOpCode();
    String[] componentNames;
    String[] componentTypes;
    if (DictionaryEntryTypeHolder.PREDEFINED_STRING_ARRAY != null) {
      componentNames = DictionaryEntryTypeHolder.PREDEFINED_STRING_ARRAY.readWithType(stream, opCode);
      opCode = stream.receiveOpCode();
      componentTypes = DictionaryEntryTypeHolder.PREDEFINED_STRING_ARRAY.readWithType(stream, opCode);
    }
    else {
      componentNames = DictionaryEntryTypeHolder.STRING_ARRAY_INSTANCE.read(stream, opCode);
      opCode = stream.receiveOpCode();
      componentTypes = DictionaryEntryTypeHolder.STRING_ARRAY_INSTANCE.read(stream, opCode);
    }
    DictionaryEntryComponent[] components = new DictionaryEntryComponent[componentNames.length];
    for (int i = 0; i < componentNames.length; i++) {
      components[i] = dictionary.createComponent(componentNames[i],
                                                 TripFactory.getInstance().getType(componentTypes[i]));
    }
    return dictionary.createEntry(ordinal, className, isProxy, components);
  }

}
