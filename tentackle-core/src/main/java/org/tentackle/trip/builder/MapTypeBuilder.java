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
import org.tentackle.trip.types.ImmutableMapTripType;
import org.tentackle.trip.types.MapTripType;
import org.tentackle.trip.types.TreeMapTripType;

import java.util.Map;
import java.util.TreeMap;

/**
 * Builder for map types.
 *
 * @param <T> the map type
 */
@TripTypeBuilderService(Map.class)
public class MapTypeBuilder<T extends Map<?, ?>> implements TripTypeBuilder<T> {

  /**
   * Creates the type builder.
   */
  public MapTypeBuilder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public TripType<T> build(Class<T> clazz) {
    if (TreeMap.class.isAssignableFrom(clazz)) {
      return new TreeMapTripType(clazz);
    }
    if (ReflectionHelper.isImmutableCollection(clazz)) {
      return new ImmutableMapTripType<>(clazz);
    }
    return new MapTripType<>(clazz);
  }

}
