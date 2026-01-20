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
import org.tentackle.trip.types.CollectionTripType;
import org.tentackle.trip.types.ImmutableCollectionTripType;
import org.tentackle.trip.types.TreeSetTripType;

import java.util.Collection;
import java.util.TreeSet;

/**
 * Builder for collection types.
 *
 * @param <T> the collection type
 */
@TripTypeBuilderService(Collection.class)
public class CollectionTypeBuilder<T extends Collection<?>> implements TripTypeBuilder<T> {

  /**
   * Creates the type builder.
   */
  public CollectionTypeBuilder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public TripType<T> build(Class<T> clazz) {
    if (TreeSet.class.isAssignableFrom(clazz)) {
      return new TreeSetTripType(clazz);
    }
    if (ReflectionHelper.isImmutableCollection(clazz)) {
      return new ImmutableCollectionTripType<>(clazz);
    }
    return new CollectionTripType<>(clazz);
  }

}
