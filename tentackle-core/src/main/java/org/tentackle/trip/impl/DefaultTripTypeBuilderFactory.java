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

package org.tentackle.trip.impl;

import org.tentackle.common.Service;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.trip.TripTypeBuilder;
import org.tentackle.trip.TripTypeBuilderFactory;
import org.tentackle.trip.builder.ObjectTypeBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default type builder factory.<br>
 * Multidimensional arrays are built by the builder for {@code Object[]}, dynamic proxies by the one for
 * {@link java.lang.reflect.Proxy}. If no builder is found, the {@link ObjectTypeBuilder} is used.
 * The builders are cached per class.
 *
 * @author harald
 */
@Service(TripTypeBuilderFactory.class)
public class DefaultTripTypeBuilderFactory implements TripTypeBuilderFactory {

  /**
   * maps data types to trip types.
   */
  private final ClassMapper builderClassMapper;

  /**
   * holds the cached type builder singletons.
   */
  private final Map<Class<?>, TripTypeBuilder<?>> builders;

  /**
   * The default type builder, if no builder is found for a data type.
   */
  private final TripTypeBuilder<?> defaultBuilder;

  /**
   * Creates the factory.
   */
  public DefaultTripTypeBuilderFactory() {
    builderClassMapper = ClassMapper.create("TripTypeBuilder", TripTypeBuilder.class);
    builders = new ConcurrentHashMap<>();
    defaultBuilder = createDefaultBuilder();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> TripTypeBuilder<T> getBuilder(Class<T> clazz) {
    return (TripTypeBuilder<T>) builders.computeIfAbsent(clazz, clz -> {
      if (clz.isArray()) {
        // reduce multi-dimensional array to single-dimensional array
        Class<?> componentType = clz.getComponentType();
        int dimensions = 1;
        while (componentType.isArray()) {
          dimensions++;
          componentType = componentType.getComponentType();
        }
        if (dimensions > 1) {
          clz = Object[].class;   // use ArrayTripTypeBuilder for multi-dimensional arrays
        }
      }
      else if (Proxy.isProxyClass(clz)) {
        // dynamic proxy classes are generated at runtime (or ahead-of-time under native-image)
        // and their synthetic name is not necessarily resolvable via Class.forName, which the
        // classmapper's lenient hierarchy walk requires. Map via the well-known Proxy.class
        // instead, which is explicitly registered and needs no hierarchy walk.
        clz = Proxy.class;
      }
      try {
        Class<?> builderClazz = builderClassMapper.mapLenient(clz);
        return (TripTypeBuilder<T>) builderClazz.getDeclaredConstructor().newInstance();
      }
      catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
             InvocationTargetException e) {
        return defaultBuilder;
      }
    });

  }


  /**
   * Creates the default type builder.
   *
   * @return the default type builder
   */
  protected TripTypeBuilder<?> createDefaultBuilder() {
    return new ObjectTypeBuilder<>();
  }

}
