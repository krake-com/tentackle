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
 * Applications should use the {@link ServiceFactory} to allow replacement of the
 * finder (see {@link Service}-annotation)
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
   * should be based on that layer's own module graph rather than the boot layer's.
   *
   * @return the module layer, null if this finder is not layer-based
   */
  default ModuleLayer getModuleLayer() {
    return null;
  }

  /**
   * Gets the finder's service path it is responsible for.
   *
   * @return the service path
   */
  String getServicePath();

  /**
   * Finds the first service configurations by service name.<br>
   * If similar configurations appear more than once on the classpath, the
   * first is returned. Useful to override service default implementations.
   *
   * @param serviceName the service name, usually a classname
   * @return the service configuration entry, never null
   * @throws TentackleRuntimeException if no such service found
   */
  Map.Entry<String, URL> findFirstServiceConfiguration(String serviceName);

  /**
   * Finds the first service provider by service name.<br>
   * If similar configurations appear more than once on the classpath, the
   * first is returned. Useful to override service default implementations.
   *
   * @param <T> the service type
   * @param service the service class
   * @return the provider class
   * @throws ClassNotFoundException if some provider could not be loaded
   */
  <T> Class<T> findFirstServiceProvider(Class<T> service) throws ClassNotFoundException;

  /**
   * Finds service configurations by service name.<br>
   * Iterations over the returned map are ordered by discovery along the classpath.
   *
   * @param serviceName the service name, usually a classname
   * @return a map of the configured services and their corresponding URLs
   */
  Map<String, URL> findServiceConfigurations(String serviceName);

  /**
   * Finds the service providers by service names.
   *
   * @param <T> the service type
   * @param service the service class
   * @return the classes providing this service
   * @throws ClassNotFoundException if some provider could not be loaded
   */
  <T> Collection<Class<T>> findServiceProviders(Class<T> service) throws ClassNotFoundException;

  /**
   * Finds URLs of a service by name.<br>
   *
   * @param serviceName the service name, usually a classname
   * @return the service's resource urls
   */
  Collection<URL> findServiceURLs(String serviceName);

  /**
   * Finds a unique service configurations by service name.<br>
   * It is checked that the service is defined exactly once.
   * Useful for factories, singletons and alike.
   *
   * @param serviceName the service name, usually a classname
   * @return the service configuration entry
   */
  Map.Entry<String, URL> findUniqueServiceConfiguration(String serviceName);

  /**
   * Finds the unique service provider by service name.<br>
   *
   * @param <T> the service type
   * @param service the service class
   * @return the provider class
   * @throws ClassNotFoundException if some provider could not be loaded
   */
  <T> Class<T> findUniqueServiceProvider(Class<T> service) throws ClassNotFoundException;


  /**
   * Creates a map of names to mapped services.<p>
   * They key of the map is the service name.
   * The value is the name providing the service which comes first in the classpath.
   *
   * @param serviceName the service class
   * @return the map
   */
  Map<String, String> createNameMap(String serviceName);

  /**
   * Creates a map of names to all mapped services.<p>
   * They key of the map is the service name.
   * The value is a list of names providing the service ordered by discovery along the classpath.
   *
   * @param serviceName the service class
   * @return the map
   */
  Map<String, Set<String>> createNameMapToAll(String serviceName);

}
