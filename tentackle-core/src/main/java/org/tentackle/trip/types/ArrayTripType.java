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
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeCategory;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.reflect.Array;


interface ArrayTypeHolder {
  TripType<Integer> INTEGER_TRIP_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
}


/**
 * Fallback for arrays of objects w/o a dedicated trip type.<br>
 * This is the core type for the majority of object arrays.
 * <p>
 * Java implements multidimensional arrays as an array of arrays, which in turn
 * are regular objects. Consequently, the memory layout contains references to subarrays,
 * a technique which is also known as "jagged arrays".
 * This also implies that the lengths of the subarrays need not necessarily be all the same!
 * As a result, we have to serialize each subarray individually.
 * <p>
 * The serialization format is as follows:
 * <ul>
 *   <li>The opcode {@link DefaultOpCode#ARRAY}.</li>
 *   <li>The dictionary entry if new for this stream.<br>
 *       Otherwise, the ordinal according to the dictionary.</li>
 *   <li>Followed by one of:
 *     <ul>
 *        <li>The null code if the array is null.</li>
 *        <li>The reference if the same array was already sent.</li>
 *        <li>The reference, the array size, and for each element, if not null:
 *          <ul>
 *            <li>The array index.</li>
 *            <li>The element's serialization.</li>
 *          </ul>
 *          followed by the opcode {@link DefaultOpCode#END}.
 *        </li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * Application-specific array types should extend this class and annotate
 * it with @{@link org.tentackle.trip.TripTypeService}.
 *
 * @param <T> the array type
 */
// created by ArrayTypeBuilder, not mapped by @TripTypeService
public final class ArrayTripType<T> extends AbstractDictionaryBasedTripType<T> {

  private final Class<?> componentClass;

  /**
   * The lazily resolved component type and components.<br>
   * The resolution must be delayed due to recursive factory calls, and trip types are shared
   * singletons, hence the fields are volatile: both are assigned only after the value is fully
   * built, so a reader either sees null or a completely initialized value.
   */
  private volatile TripType<?> componentType;
  private volatile TripComponent<T, ?>[] components;

  /**
   * Creates the array type.
   *
   * @param clazz the array class
   */
  public ArrayTripType(Class<T> clazz) {
    super(clazz, TripTypeCategory.ARRAY);
    componentClass = clazz.getComponentType();
  }

  /**
   * Gets the component class of the array.
   *
   * @return the component class
   */
  public Class<?> getComponentClass() {
    return componentClass;
  }

  /**
   * Gets the component type of the array.
   *
   * @return the component type
   */
  public TripType<?> getComponentType() {
    TripType<?> type = componentType;
    if (type == null) {
      // must be delayed due to recursive factory calls
      type = TripFactory.getInstance().getType(componentClass);
      componentType = type;
    }
    return type;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public TripComponent<T, ?>[] getComponents() {
    TripComponent<T, ?>[] tripComponents = components;
    if (tripComponents == null) {
      // deliberately unlocked: concurrent readers may build it more than once, but the components
      // are equivalent and matched by name (see DefaultDictionaryEntry), not by identity.
      tripComponents = new TripComponent[]{
        TripFactory.getInstance().createComponent(this, 0, componentClass)
      };
      components = tripComponents;
    }
    return tripComponents;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void write(T value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    serializer.serializeOpCode(DefaultOpCode.ARRAY);
    sendDictionary(stream);   // send the component class
    if (value == null) {
      sendNull(stream);
    }
    else {
      Reference<T> reference = sendReference(stream, value);
      if (reference == null || reference.isObjectFollowing()) {
        int length = Array.getLength(value);
        ArrayTypeHolder.INTEGER_TRIP_TYPE.write(length, stream);
        for (int arrayIndex = 0; arrayIndex < length; arrayIndex++) {
          Object element = Array.get(value, arrayIndex);
          if (element != null) {
            // if the component type is not final: determine the type dynamically
            TripType<?> type = getComponentType();
            TripType elementType = type.isFinal() ? type : stream.getType(element.getClass());
            ArrayTypeHolder.INTEGER_TRIP_TYPE.write(arrayIndex, stream);
            elementType.write(element, stream);
          }
        }
        sendEnd(stream);
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T read(TripStream stream, OpCode opCode) {
    DictionaryEntry entry = null;

    if (opCode == DefaultOpCode.ARRAY) {
      // determine the dictionary entry from the stream, if provided
      opCode = stream.receiveOpCode();
      if (opCode == DefaultOpCode.DICTIONARY || opCode.isDictionaryOrdinal()) {
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

    T value = null;
    boolean readContent = true;
    Reference<T> reference = null;
    if (opCode.isReference()) {
      reference = receiveReference(stream, opCode);
      readContent = reference.isObjectFollowing();
      if (readContent) {
        opCode = stream.receiveOpCode();    // fetch the next opcode for loop below
      }
      else {
        value = reference.getObject();
        if (value == null) {
          throw new TripRuntimeException("reference " + reference + " has no array object");
        }
      }
    }

    if (readContent) {
      int length = checkSize(ArrayTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode));
      opCode = stream.receiveOpCode();
      value = (T) Array.newInstance(getComponentClass(entry), length);

      if (reference != null) {
        stream.addReference(reference, value, false);
        reference.markReceived();
      }

      while (opCode != DefaultOpCode.END) {
        int arrayIndex = ArrayTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode);
        if (arrayIndex < 0 || arrayIndex >= length) {
          throw new TripRuntimeException("array index " + arrayIndex + " out of range for " + this +
                                         " of length " + length);
        }
        opCode = stream.receiveOpCode();
        TripType<?> elementType = getComponentType();
        if (!elementType.isFinal()) {
          elementType = stream.deriveType(opCode);
          if (elementType.isDictionaryBased()) {
            opCode = stream.receiveOpCode();
          }
        }
        Object element = elementType.read(stream, opCode);
        Array.set(value, arrayIndex, element);
        // next element
        opCode = stream.receiveOpCode();
      }
    }
    return value;
  }


  private Class<?> getComponentClass(DictionaryEntry entry) {
    String className = entry.getComponents()[0].getComponentName();
    try {
      return ReflectionHelper.loadClass(className);
    }
    catch (ClassNotFoundException e) {
      throw new TripRuntimeException("cannot load component class " + className + " for " + this, e);
    }
  }

}
