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

import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripPolicyViolationException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.EnumMap;
import java.util.Iterator;


interface EnumMapTypeHolder {
  TripType<Integer> INTEGER_TRIP_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
  @SuppressWarnings("rawtypes")
  TripType<Class> CLASS_TRIP_TYPE = TripFactory.getInstance().getType(Class.class);
}


/**
 * Trip type for {@link EnumMap}s.
 * <p>
 * EnumMaps provide no sized or no-args constructor. For non-empty maps the key type
 * is serialized in the header instead, and the map is created via the
 * {@link EnumMap#EnumMap(Class)} constructor. Since the key type of an empty EnumMap
 * cannot be determined via public API, empty maps are serialized via JOS.
 * <p>
 * The JOS stream of an empty map is read through a deserialization filter that admits nothing but the
 * {@code EnumMap} and its enum key type. Otherwise, any peer, authenticated or not, could pass an arbitrary
 * Java-serialized object graph through it.
 *
 * @param <T> the enum map type
 */
// created by MapTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class EnumMapTripType<T extends EnumMap> extends MapTripType<T> {

  /**
   * Admits only what the serialized form of an empty {@link EnumMap} consists of:
   * the map itself and its key type, an enum class.
   */
  private static final ObjectInputFilter EMPTY_ENUM_MAP_FILTER = info -> {
    if (info.depth() > 8 || info.references() > 64 || info.streamBytes() > 16384 || info.arrayLength() >= 0) {
      return ObjectInputFilter.Status.REJECTED;
    }
    Class<?> clazz = info.serialClass();
    if (clazz == null) {
      return ObjectInputFilter.Status.UNDECIDED;    // limits only, checked above
    }
    return clazz == EnumMap.class || Enum.class.isAssignableFrom(clazz) ?
           ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
  };

  /**
   * Creates the type.
   *
   * @param clazz the enum map's class
   */
  public EnumMapTripType(Class<T> clazz) {
    super(clazz);
  }

  @Override
  public boolean isInstantiable() {
    return false;   // no sized or no-args constructor: created via EnumMap(Class) instead
  }

  @Override
  protected void writeHeader(T value, TripStream stream) {
    super.writeHeader(value, stream);
    Serializer serializer = stream.getSerializer();
    Iterator<?> keyIterator = value.keySet().iterator();
    if (keyIterator.hasNext()) {
      serializer.serializeBoolean(true);
      EnumMapTypeHolder.CLASS_TRIP_TYPE.write(((Enum<?>) keyIterator.next()).getDeclaringClass(), stream);
    }
    else {
      serializer.serializeBoolean(false);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (ObjectOutputStream os = new ObjectOutputStream(bos)) {
        os.writeObject(value);
      }
      catch (IOException iox) {
        throw new TripRuntimeException("cannot serialize empty enum map", iox);
      }
      serializer.serializeInt(bos.size());
      serializer.serialize(bos.toByteArray(), 0, bos.size());
    }
  }


  @Override
  @SuppressWarnings("unchecked")
  protected MapHeader<T> readHeader(TripStream stream, OpCode opCode) {
    int size = checkSize(stream, EnumMapTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode));
    Deserializer deserializer = stream.getDeserializer();
    if (deserializer.deserializeBoolean()) {
      Class keyType = EnumMapTypeHolder.CLASS_TRIP_TYPE.read(stream, stream.receiveOpCode());
      return new MapHeader<>(size, (T) new EnumMap(keyType));
    }
    // read while the data arrives: the size is not trusted
    DeserializerInputStream data = new DeserializerInputStream(deserializer, checkSize(stream, deserializer.deserializeInt()));
    try (ObjectInputStream is = new ObjectInputStream(data)) {
      is.setObjectInputFilter(EMPTY_ENUM_MAP_FILTER);
      Object object = is.readObject();
      if (!(object instanceof EnumMap<?, ?> map) || !map.isEmpty()) {
        throw new TripPolicyViolationException("serialized empty enum map expected, got " +
                                               (object == null ? "null" : EffectiveClassProvider.getEffectiveClass(object)));
      }
      data.drain();   // nothing left, unless the map was written with trailing data
      return new MapHeader<>(size, (T) map);
    }
    catch (InvalidClassException e) {
      throw new TripPolicyViolationException("serialized empty enum map refused by the deserialization filter: " +
                                             e.getMessage());
    }
    catch (IOException | ClassNotFoundException e) {
      throw new TripRuntimeException("cannot deserialize empty enum map", e);
    }
  }

}
