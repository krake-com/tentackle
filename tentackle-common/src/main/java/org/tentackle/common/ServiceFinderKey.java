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

package org.tentackle.common;

import java.util.Objects;

/**
 * Key to map service finders.
 * <p>
 * Finders bound to a {@link ModuleLayer} are keyed separately from plain classloader-based finders,
 * even if the layer's classloader is the same, since only the former are layer-aware
 * (see {@link ServiceFinder#getModuleLayer()}).
 * The service path is normalized the same way as {@link DefaultServiceFinder} does,
 * so that {@code "META-INF/services"} and {@code "/META-INF/services/"} map to the same key.
 *
 * @param classLoader the classloader
 * @param layer the module layer, null if the finder is not bound to a layer
 * @param servicePath the service path
 *
 * @author harald
 */
public record ServiceFinderKey(ClassLoader classLoader, ModuleLayer layer, String servicePath) {

  /**
   * Creates a key.
   *
   * @param classLoader the classloader, never null
   * @param layer the module layer, null if the finder is not bound to a layer
   * @param servicePath the service path, never null
   * @throws NullPointerException if classLoader or servicePath is null
   */
  public ServiceFinderKey {
    Objects.requireNonNull(classLoader, "classLoader");
    servicePath = DefaultServiceFinder.normalizeServicePath(Objects.requireNonNull(servicePath, "servicePath"));
  }

  /**
   * Creates a key for a finder that is not bound to a module layer.
   *
   * @param classLoader the classloader, never null
   * @param servicePath the service path, never null
   * @throws NullPointerException if classLoader or servicePath is null
   */
  public ServiceFinderKey(ClassLoader classLoader, String servicePath) {
    this(classLoader, null, servicePath);
  }
}
