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

package org.tentackle.trip.builder;

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeBuilder;
import org.tentackle.trip.TripTypeBuilderService;
import org.tentackle.trip.types.EnumMapTripType;
import org.tentackle.trip.types.ImmutableMapTripType;
import org.tentackle.trip.types.MapTripType;
import org.tentackle.trip.types.TreeMapTripType;
import org.tentackle.trip.types.WrappedMapTripType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Builder for map types.
 *
 * @param <T> the map type
 */
@TripTypeBuilderService(Map.class)
public class MapTypeBuilder<T extends Map<?, ?>> implements TripTypeBuilder<T> {

  /** the JDK maps without usable constructors, mapped to their wrapper functions. */
  private static final Map<Class<?>, Function<LinkedHashMap<Object, Object>, Map<?, ?>>> WRAPPED_TYPES = createWrappedTypes();

  private static Map<Class<?>, Function<LinkedHashMap<Object, Object>, Map<?, ?>>> createWrappedTypes() {
    Map<Class<?>, Function<LinkedHashMap<Object, Object>, Map<?, ?>>> wrappedTypes = new HashMap<>();
    wrappedTypes.put(Collections.unmodifiableMap(new HashMap<>()).getClass(),
                     map -> Collections.unmodifiableMap(map));
    wrappedTypes.put(Collections.emptyMap().getClass(), map -> Collections.emptyMap());
    wrappedTypes.put(Collections.singletonMap(Boolean.TRUE, Boolean.TRUE).getClass(),
                     map -> {
                       Map.Entry<Object, Object> entry = map.entrySet().iterator().next();
                       return Collections.singletonMap(entry.getKey(), entry.getValue());
                     });
    return Map.copyOf(wrappedTypes);
  }


  /**
   * Creates the type builder.
   */
  public MapTypeBuilder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public TripType<T> build(Class<T> clazz) {
    Function<LinkedHashMap<Object, Object>, Map<?, ?>> wrapper = WRAPPED_TYPES.get(clazz);
    if (wrapper != null) {
      return new WrappedMapTripType(clazz, wrapper);
    }
    if (EnumMap.class.isAssignableFrom(clazz)) {
      return new EnumMapTripType(clazz);
    }
    if (TreeMap.class.isAssignableFrom(clazz)) {
      return new TreeMapTripType(clazz);
    }
    if (ReflectionHelper.isImmutableCollection(clazz)) {
      return new ImmutableMapTripType<>(clazz);
    }
    return new MapTripType<>(clazz);
  }

}
