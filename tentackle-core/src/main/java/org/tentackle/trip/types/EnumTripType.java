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

import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeCategory;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.EnumTripComponent;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Fallback for enums w/o a dedicated trip type.<br>
 * This is the core type for the majority of enums.
 * <p>
 * The serialization format is as follows:
 * <ul>
 *   <li>The opcode {@link DefaultOpCode#ENUM}.</li>
 *   <li>The dictionary entry if new for this stream.<br>
 *       Otherwise, the ordinal according to the dictionary.</li>
 *   <li>Followed by one of:
 *     <ul>
 *       <li>The null code if the enum is null.</li>
 *       <li>The enum value's ordinal, according to the dictionary.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * All the sender's enum constants must be declared in the receiver as well.
 * However, the order of the enum constants is not important.
 *
 * @param <T> the type
 */
// created by EnumTypeBuilder, not mapped by @TripTypeService
public class EnumTripType<T extends Enum<T>> extends AbstractDictionaryBasedTripType<T> {

  private final TripComponent<T, T>[] components;

  /**
   * Creates the type.
   *
   * @param clazz the enum's class
   */
  public EnumTripType(Class<T> clazz) {
    super(clazz, TripTypeCategory.ENUM);
    components = createComponents();
  }

  @Override
  public boolean isReferencable(T value) {
    return false;
  }

  @Override
  public void write(T value, TripStream stream) {
    stream.getSerializer().serializeOpCode(DefaultOpCode.ENUM);
    sendDictionary(stream);
    if (value == null) {
      sendNull(stream);
    }
    else {
      stream.writeOrdinal(value.ordinal());
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T read(TripStream stream, OpCode opCode) {
    DictionaryEntry entry = null;

    if (opCode == DefaultOpCode.ENUM) {
      // determine the dictionary entry from the stream, if provided
      opCode = stream.receiveOpCode();
      if (opCode == DefaultOpCode.DICTIONARY || opCode.isOrdinal()) {
        entry = stream.receiveDictionary(opCode);
        opCode = stream.receiveOpCode();
      }

      if (opCode == DefaultOpCode.NULL) {
        return null;
      }
    }
    // else: invoked as a component of another object (see ObjectTripType#readComponents)

    if (entry == null) {
      // invoked as a component of another object.
      // this trip type is already the runtime-type!
      entry = stream.getDictionaryEntry(getObjectClass());
    }

    int ordinal = stream.readOrdinal(opCode);
    EnumTripComponent<T> component = (EnumTripComponent<T>) entry.getTypeComponent(ordinal);
    if (component == null) {
      throw new TripRuntimeException(getObjectClass() + " does not provide the enum constant " +
                                     entry.getComponent(ordinal).getComponentName());
    }
    return component.getDefaultValue();
  }

  @Override
  public TripComponent<T, T>[] getComponents() {
    return components;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private TripComponent<T, T>[] createComponents() {
    Collection<TripComponent<T, T>> tripComponents = new ArrayList<>();
    for (T enumConstant : getObjectClass().getEnumConstants()) {
      tripComponents.add(TripFactory.getInstance().createComponent(this, enumConstant));
    }
    return tripComponents.toArray(new TripComponent[0]);
  }

}
