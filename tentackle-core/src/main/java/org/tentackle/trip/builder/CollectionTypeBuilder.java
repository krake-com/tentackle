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

import org.tentackle.misc.CopyOnWriteCollection;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeBuilder;
import org.tentackle.trip.TripTypeBuilderService;
import org.tentackle.trip.types.CollectionTripType;
import org.tentackle.trip.types.EnumSetTripType;
import org.tentackle.trip.types.ImmutableCollectionTripType;
import org.tentackle.trip.types.ObjectTripType;
import org.tentackle.trip.types.TreeSetTripType;
import org.tentackle.trip.types.WrappedCollectionTripType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Builder for collection types.
 *
 * @param <T> the collection type
 */
@TripTypeBuilderService(Collection.class)
public class CollectionTypeBuilder<T extends Collection<?>> implements TripTypeBuilder<T> {

  /** the JDK collections without usable constructors, mapped to their wrapper functions. */
  private static final Map<Class<?>, Function<ArrayList<Object>, Collection<?>>> WRAPPED_TYPES = createWrappedTypes();

  private static Map<Class<?>, Function<ArrayList<Object>, Collection<?>>> createWrappedTypes() {
    Map<Class<?>, Function<ArrayList<Object>, Collection<?>>> wrappedTypes = new HashMap<>();
    wrappedTypes.put(Collections.unmodifiableCollection(List.of()).getClass(),
                     list -> Collections.unmodifiableCollection(list));
    wrappedTypes.put(Collections.unmodifiableList(new LinkedList<>()).getClass(),
                     list -> Collections.unmodifiableList(list));
    wrappedTypes.put(Collections.unmodifiableList(new ArrayList<>()).getClass(),   // the RandomAccess variant
                     list -> Collections.unmodifiableList(list));
    wrappedTypes.put(Collections.unmodifiableSet(new HashSet<>()).getClass(),
                     list -> Collections.unmodifiableSet(new LinkedHashSet<>(list)));
    wrappedTypes.put(Collections.emptyList().getClass(), list -> Collections.emptyList());
    wrappedTypes.put(Collections.emptySet().getClass(), list -> Collections.emptySet());
    wrappedTypes.put(Collections.singletonList(Boolean.TRUE).getClass(), list -> Collections.singletonList(list.get(0)));
    wrappedTypes.put(Collections.singleton(Boolean.TRUE).getClass(), list -> Collections.singleton(list.get(0)));
    wrappedTypes.put(Arrays.asList().getClass(), list -> Arrays.asList(list.toArray()));
    return Map.copyOf(wrappedTypes);
  }


  /**
   * Creates the type builder.
   */
  public CollectionTypeBuilder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public TripType<T> build(Class<T> clazz) {
    Function<ArrayList<Object>, Collection<?>> wrapper = WRAPPED_TYPES.get(clazz);
    if (wrapper != null) {
      return new WrappedCollectionTripType(clazz, wrapper);
    }
    if (EnumSet.class.isAssignableFrom(clazz)) {
      return new EnumSetTripType(clazz);
    }
    if (TreeSet.class.isAssignableFrom(clazz)) {
      return new TreeSetTripType(clazz);
    }
    if (ReflectionHelper.isImmutableCollection(clazz)) {
      return new ImmutableCollectionTripType<>(clazz);
    }
    if (CopyOnWriteCollection.class.isAssignableFrom(clazz)) {
      // does not extend a JDK collection, faster with ObjectTripType
      return new ObjectTripType<>(clazz);
    }
    return new CollectionTripType<>(clazz);
  }

}
