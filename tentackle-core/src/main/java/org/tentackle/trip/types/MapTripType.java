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
import org.tentackle.trip.Reference;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripInstantiationException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;


interface MapTypeHolder {
  TripType<Integer> INTEGER_TRIP_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
}


/**
 * Trip type for {@link Map}s.
 *
 * @param <T> the map type
 */
// created by MapTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class MapTripType<T extends Map> extends ObjectTripType<T> {

  private static final OpCode OP_CODE = DefaultOpCode.MAP;

  private Constructor<T> sizedConstructor;
  private Constructor<T> noArgsConstructor;

  /**
   * Creates the type.
   *
   * @param clazz the map's class
   */
  public MapTripType(Class<T> clazz) {
    super(clazz);
    if (isInstantiable()) {
      try {
        sizedConstructor = clazz.getDeclaredConstructor(Integer.TYPE);
      }
      catch (NoSuchMethodException e) {
        sizedConstructor = null;
        try {
          noArgsConstructor = clazz.getDeclaredConstructor();
        }
        catch (NoSuchMethodException ex) {
          throw new TripInstantiationException(clazz, "no sized or a no-args constructor", ex);
        }
      }
    }
    // else: interface or abstract. Since the concrete type is not known yet,
    // it will be determined when serialized. Therefore, read() and write() won't be invoked.
  }

  /**
   * Gets the sized constructor.
   *
   * @return the sized constructor, null if none
   */
  public Constructor<T> getSizedConstructor() {
    return sizedConstructor;
  }

  /**
   * Gets the no-args constructor.
   *
   * @return the no-args constructor, null if none
   */
  public Constructor<T> getNoArgsConstructor() {
    return noArgsConstructor;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void write(T value, TripStream stream) {
    stream.getSerializer().serializeOpCode(getOpCode());
    sendDictionary(stream);
    if (value == null) {
      sendNull(stream);
    }
    else {
      Reference<T> reference = sendReference(stream, value);
      if (reference == null || reference.isObjectFollowing()) {
        prepareWrite(value);
        writeHeader(value, stream);
        Set<Map.Entry> entrySet = value.entrySet();
        for (Map.Entry element : entrySet) {
          Object elementKey = element.getKey();
          if (elementKey == null) {
            sendNull(stream);   // map allows null keys, obviously
          }
          else {
            TripType type = stream.getType(elementKey.getClass());
            type.write(elementKey, stream);
          }
          Object elementValue = element.getValue();
          if (elementValue == null) {
            sendNull(stream);   // map allows null values, obviously
          }
          else {
            TripType type = stream.getType(elementValue.getClass());
            type.write(elementValue, stream);
          }
        }
        if (getComponents().length > 0) {
          // additional members (TripComponentProvider!)
          writeComponents(value, stream);
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T read(TripStream stream, OpCode opCode, Consumer<T> initializer) {
    DictionaryEntry entry = null;

    if (opCode == getOpCode()) {
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
    // else: non-final type invoked as a component of another object (see ObjectTripType#readComponents)

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
          throw new TripRuntimeException("reference " + reference + " has no collection object");
        }
      }
    }

    if (readContent) {
      MapHeader<T> header = readHeader(stream, opCode);
      int size = header.size();
      value = header.value();
      if (initializer != null) {
        initializer.accept(value);
      }

      if (reference != null && !isConverted()) {
        // register before reading the entries, so that keys or values may back-reference the map itself
        stream.addReference(reference, value, isDeduplicateable(value));
        reference.markReceived();
      }

      if (size > 0) {
        opCode = stream.receiveOpCode();
        int elementCount = 0;
        while (elementCount < size) {
          Object elementKey;
          if (opCode == DefaultOpCode.NULL) {
            elementKey = null;    // map allows null keys, obviously
          }
          else {
            TripType<?> elementType = stream.deriveType(opCode);
            if (elementType.isDictionaryBased()) {
              opCode = stream.receiveOpCode();
            }
            elementKey = elementType.read(stream, opCode);
          }

          opCode = stream.receiveOpCode();
          Object elementValue;
          if (opCode == DefaultOpCode.NULL) {
            elementValue = null;    // map allows null values, obviously
          }
          else {
            TripType<?> elementType = stream.deriveType(opCode);
            if (elementType.isDictionaryBased()) {
              opCode = stream.receiveOpCode();
            }
            elementValue = elementType.read(stream, opCode);
          }

          value.put(elementKey, elementValue);

          if (++elementCount < size) {
            // opcode for the next element
            opCode = stream.receiveOpCode();
          }
        }
      }

      if (getComponents().length > 0) {
        value = readComponents(stream, stream.receiveOpCode(), entry, reference, value, initializer);
      }

      value = convert(value);
      validateRead(value);

      if (reference != null && isConverted()) {
        stream.addReference(reference, value, isDeduplicateable(value));
        reference.markReceived();
      }
    }
    return value;
  }

  /**
   * Converts the map.<br>
   * The default implementation does nothing.<br>
   * Provided for {@link ImmutableMapTripType}.
   *
   * @param value the map read from the stream (not from reference!)
   * @return the converted map
   */
  protected T convert(T value) {
    return value;
  }

  /**
   * Tells whether {@link #convert(Map)} returns a different instance.<br>
   * Non-converted maps are registered as references before reading the entries,
   * so that keys or values may back-reference the map itself. Converted maps
   * can only be registered after the conversion.
   *
   * @return true if the map is converted
   */
  protected boolean isConverted() {
    return false;
  }

  @Override
  protected OpCode getOpCode() {
    return OP_CODE;
  }

  /**
   * Creates a new map instance.
   *
   * @param size the size of the map
   * @return the map instance
   */
  protected T createMap(int size) {
    try {
      if (sizedConstructor != null) {
        return sizedConstructor.newInstance(size);
      }
      else {
        return noArgsConstructor.newInstance();
      }
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new TripInstantiationException(getObjectClass(), "cannot instantiate map", e);
    }
  }

  @Override
  protected T createInitializedDefault() {
    return null;    // delayed construction until the size is known
  }

  @Override
  protected boolean isFieldSerializable(Field field) {
    // The fields in the java maps must not be serialized, since the components are serialized instead.
    // Otherwise, we would get exceptions "module java.base does not open java.util to module org.tentackle.core"
    return super.isFieldSerializable(field) && !field.getDeclaringClass().getName().startsWith("java.");
  }

  /**
   * Writes the map header.
   *
   * @param value the map instance
   * @param stream the stream to write to
   */
  protected void writeHeader(T value, TripStream stream) {
    MapTypeHolder.INTEGER_TRIP_TYPE.write(value.size(), stream);
  }

  /**
   * Returned by {@link #readHeader(TripStream, OpCode)}.
   *
   * @param size the size of the map
   * @param value the map instance
   * @param <T> the map type
   */
  protected record MapHeader<T>(int size, T value) {}

  /**
   * Reads the map header and creates a new map instance.
   *
   * @param stream the stream to read from
   * @param opCode the opcode to read (already read from the stream)
   * @return the map header
   */
  protected MapHeader<T> readHeader(TripStream stream, OpCode opCode) {
    int size = checkSize(MapTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode));
    // the map grows while its entries are read, so the size is only a capacity hint
    return new MapHeader<>(size, createMap(limitPreAllocation(size)));
  }

}
