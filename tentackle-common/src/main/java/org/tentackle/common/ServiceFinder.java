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

import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Finds service configurations.
 * <p>
 * Very similar to {@link java.util.ServiceLoader} but provides additional methods to
 * load configurations without being forced to load the classes.<br>
 * Furthermore, it is not restricted to {@code META-INF/services} and the contents
 * of the configuration file. Supports dedicated classloaders for services as well.
 * <p>
 * The configuration files are read in the order determined by the {@link ModuleSorter}:
 * named modules according to their dependencies (the topmost module first), followed by the classpath
 * and automatic modules (unless the service is annotated with {@link ClasspathFirst}).
 * Throughout this interface, this is referred to as the "module order".
 * <p>
 * Applications should use the {@link ServiceFactory} to allow replacement of the
 * finder (see {@link Service}-annotation).
 *
 * @author harald
 */
public interface ServiceFinder {

  /**
   * Gets the default classloader used by this finder.
   *
   * @return the classloader, never null
   */
  ClassLoader getClassLoader();

  /**
   * Gets the module layer this finder looks up services in, if any.<br>
   * A non-null layer means {@link #getClassLoader()} is the layer's own classloader
   * (see {@link ModuleLayerBuilder}), and module ordering for this finder's service lookups
   * is based on that layer's own module graph rather than the one visible from the context classloader.
   *
   * @return the module layer, null if this finder is not layer-based
   */
  default ModuleLayer getModuleLayer() {
    return null;
  }

  /**
   * Gets the finder's service path it is responsible for.<br>
   * The path is normalized: it never starts with a slash and always ends with one,
   * for example {@code "META-INF/services/"}.
   *
   * @return the service path
   */
  String getServicePath();

  /**
   * Finds the first service configuration by service name.<br>
   * If similar configurations appear more than once, the first in module order
   * is returned. Useful to override service default implementations.
   *
   * @param serviceName the service name, usually a classname
   * @return the service configuration entry, never null
   * @throws TentackleRuntimeException if no such service found
   */
  Map.Entry<String, URL> findFirstServiceConfiguration(String serviceName);

  /**
   * Finds the first service provider by service class.<br>
   * If similar configurations appear more than once, the first in module order
   * is returned. Useful to override service default implementations.
   *
   * @param <T> the service type
   * @param service the service class
   * @return the provider class
   * @throws ClassNotFoundException if some provider could not be loaded
   * @throws TentackleRuntimeException if no such service found or the provider does not implement the service
   */
  <T> Class<T> findFirstServiceProvider(Class<T> service) throws ClassNotFoundException;

  /**
   * Finds service configurations by service name.<br>
   * Iterations over the returned map are ordered by discovery in module order.
   * The keys are the configuration lines, the values the URLs of the files they were found in.
   * The same configuration line in two different files is considered an error,
   * since it usually indicates a misconfiguration.
   *
   * @param serviceName the service name, usually a classname
   * @return a map of the configured services and their corresponding URLs
   * @throws TentackleRuntimeException if a configuration could not be read or is defined more than once
   */
  Map<String, URL> findServiceConfigurations(String serviceName);

  /**
   * Finds the service providers by service class.<br>
   * The providers are ordered by discovery in module order.
   *
   * @param <T> the service type
   * @param service the service class
   * @return the classes providing this service
   * @throws ClassNotFoundException if some provider could not be loaded
   * @throws TentackleRuntimeException if some provider does not implement the service
   */
  <T> Collection<Class<T>> findServiceProviders(Class<T> service) throws ClassNotFoundException;

  /**
   * Finds URLs of a service by name.<br>
   * The URLs are sorted in module order.
   *
   * @param serviceName the service name, usually a classname
   * @return the service's resource URLs
   * @throws TentackleRuntimeException if the resources could not be determined
   */
  Collection<URL> findServiceURLs(String serviceName);

  /**
   * Finds a unique service configuration by service name.<br>
   * It is checked that the service is defined exactly once.
   * Useful for factories, singletons and alike.
   *
   * @param serviceName the service name, usually a classname
   * @return the service configuration entry
   * @throws TentackleRuntimeException if no such service found or the service is defined more than once
   */
  Map.Entry<String, URL> findUniqueServiceConfiguration(String serviceName);

  /**
   * Finds the unique service provider by service class.<br>
   * It is checked that the service is defined exactly once.
   *
   * @param <T> the service type
   * @param service the service class
   * @return the provider class
   * @throws ClassNotFoundException if some provider could not be loaded
   * @throws TentackleRuntimeException if no such service found, the service is defined more than once,
   *         or the provider does not implement the service
   */
  <T> Class<T> findUniqueServiceProvider(Class<T> service) throws ClassNotFoundException;


  /**
   * Creates a map of names to mapped services.<br>
   * The configurations are taken from {@link Constants#MAPPED_SERVICE_PATH} and have the form
   * {@code provider = name} (or {@code provider : name}), as generated for {@link MappedService}s.
   * <p>
   * The key of the map is the mapped name (the right side, unquoted if quoted).
   * The value is the name of the provider (the left side) that comes first in module order.
   *
   * @param serviceName the service name, usually the classname of the mapped service
   * @return the map
   * @throws TentackleRuntimeException if some configuration is malformed
   */
  Map<String, String> createNameMap(String serviceName);

  /**
   * Creates a map of names to all mapped services.<br>
   * Same as {@link #createNameMap(String)}, but maps each name to all of its providers.
   * <p>
   * The key of the map is the mapped name (the right side).
   * The value is the unordered set of names of all providers (the left side).
   *
   * @param serviceName the service name, usually the classname of the mapped service
   * @return the map
   * @throws TentackleRuntimeException if some configuration is malformed
   */
  Map<String, Set<String>> createNameMapToAll(String serviceName);

}
