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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default implementation of a {@link ServiceFinder}.
 *
 * @author harald
 */
// @Service does not work in tentackle-common, since there is no analyze maven plugin available at this build step yet.
// Needs explicit config in resources/services/META-INF.
@Service(ServiceFinder.class)
public class DefaultServiceFinder implements ServiceFinder {
  /**
   * Notice: @Service annotation cannot be used because tentackle-maven-plugin depends
   * on tentackle-common, i.e. this cannot be analyzed.
   * META-INF/services must be edited manually!
   */

  private final ClassLoader loader;                         // the default classloader, never null
  private final ModuleLayer layer;                          // optional module layer, null if not layer-based
  private final String servicePath;                         // optional service path, null = SERVICE_PATH
  private final Map<String,Map<String,URL>> serviceMap;     // cached configs
  private final Map<String,Collection<Class<?>>> classMap;  // cached classes


  /**
   * Creates a service finder.<br>
   *
   * @param loader optional classloader, null if use {@link ClassLoader#getSystemClassLoader()}
   * @param servicePath optional service path prefix, null if {@code "META_INF/services/"}
   */
  public DefaultServiceFinder(ClassLoader loader, String servicePath) {
    this(loader, null, servicePath);
  }


  /**
   * Creates a service finder looking up services within a module layer.<br>
   * The layer must have been built with exactly one classloader for all its modules
   * (see {@link ModuleLayerBuilder}), since that single loader is what {@link #getClassLoader()}
   * returns and what resource lookups are performed against.
   *
   * @param layer the module layer
   * @param servicePath optional service path prefix, null if {@code "META_INF/services/"}
   */
  public DefaultServiceFinder(ModuleLayer layer, String servicePath) {
    this(resolveLayerLoader(layer), layer, servicePath);
  }


  private DefaultServiceFinder(ClassLoader loader, ModuleLayer layer, String servicePath) {

    this.loader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
    this.layer = layer;

    if (this.loader == null) {
      throw new TentackleRuntimeException("cannot determine classloader");
    }

    if (servicePath == null) {
      this.servicePath = Constants.DEFAULT_SERVICE_PATH;
    }
    else  {
      while (servicePath.startsWith("/")) {
        // cut leading slashes, if any
        servicePath = servicePath.substring(1);
      }
      if (!servicePath.endsWith("/")) {
        // add trailing slash if missing
        servicePath += "/";
      }
      this.servicePath = servicePath;
    }

    serviceMap = new HashMap<>();
    classMap = new HashMap<>();
  }


  /**
   * Creates a default service finder.<br>
   * The classloader used is {@code Thread.currentThread().getContextClassLoader()}.
   *
   * @param servicePath optional service path prefix, null if {@code "META_INF/services/"}
   */
  public DefaultServiceFinder(String servicePath) {
    this(Thread.currentThread().getContextClassLoader(), servicePath);
  }


  /**
   * Creates a default service finder.<br>
   * The classloader used is {@code Thread.currentThread().getContextClassLoader()}.<br>
   * The servicepath is {@code "META_INF/services/"}.
   */
  public DefaultServiceFinder() {
    this(null);
  }


  /**
   * Gets the classloader used by this finder.
   *
   * @return the classloader, never null
   */
  @Override
  public ClassLoader getClassLoader() {
    return loader;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ModuleLayer getModuleLayer() {
    return layer;
  }

  /**
   * Gets the service path the finder is responsible for.
   *
   * @return the service path
   */
  @Override
  public String getServicePath() {
    return servicePath;
  }


  /**
   * Resolves the single classloader of a one-loader-per-layer module layer.
   *
   * @param layer the module layer
   * @return the layer's classloader
   */
  static ClassLoader resolveLayerLoader(ModuleLayer layer) {
    Objects.requireNonNull(layer, "layer");
    return layer.modules().stream()
            .findFirst()
            .map(Module::getClassLoader)
            .orElseThrow(() -> new TentackleRuntimeException("module layer has no modules"));
  }


  /**
   * Resolves the module sorter to use for this finder's service lookups.<br>
   * Layer-based finders use a sorter scoped to their own layer, so module ordinals
   * reflect that layer's own module graph rather than the boot layer's.
   *
   * @return the module sorter
   */
  private ModuleSorter resolveModuleSorter() {
    return layer == null ? ModuleSorter.INSTANCE : ModuleSorter.forLayer(layer);
  }


  /**
   * Finds URLs of a service by name.<br>
   *
   * @param serviceName the service name, usually a classname
   * @return the service's resource urls
   */
  @Override
  public Collection<URL> findServiceURLs(String serviceName) {
    try {
      Enumeration<URL> resources = getClassLoader(serviceName).getResources(servicePath + serviceName);
      return resolveModuleSorter().sort(resources, serviceName);
    }
    catch (IOException iox) {
      throw new TentackleRuntimeException("loading service information failed", iox);
    }
  }


  /**
   * Finds service configurations by service name.<br>
   * Iterations over the returned map are ordered by discovery along the classpath.
   *
   * @param serviceName the service name, usually a classname
   * @return a map of the configured services and their corresponding URLs
   */
  @Override
  public synchronized Map<String,URL> findServiceConfigurations(String serviceName) {
    return serviceMap.computeIfAbsent(serviceName, sn -> {
      Map<String, URL> services = new LinkedHashMap<>();   // linked hashmap to preserve order
      parseURLs(findServiceURLs(sn), sn, services);
      return services;
    });
  }


  /**
   * Finds the first service configurations by service name.<br>
   * If similar configurations appear more than once on the classpath, the
   * first is returned. Useful to override service default implementations.
   *
   * @param serviceName the service name, usually a classname
   * @return the service configuration entry
   */
  @Override
  public synchronized Map.Entry<String,URL> findFirstServiceConfiguration(String serviceName) {
    Map<String, URL> services = findServiceConfigurations(serviceName);
    if (services.isEmpty()) {
      throw new TentackleRuntimeException("service '" + serviceName + "' not found");
    }
    return services.entrySet().iterator().next();
  }


  /**
   * Finds a unique service configurations by service name.<br>
   * It is checked that the service is defined exactly once.
   * Useful for factories, singletons and alike.
   *
   * @param serviceName the service name, usually a classname
   * @return the service configuration entry
   */
  @Override
  public synchronized Map.Entry<String,URL> findUniqueServiceConfiguration(String serviceName) {
    Map<String, URL> services = findServiceConfigurations(serviceName);
    if (services.isEmpty()) {
      throw new TentackleRuntimeException("service '" + serviceName + "' not found");
    }
    if (services.size() > 1) {
      StringBuilder msg = new StringBuilder();
      msg.append("service '").append(serviceName).append("' defined more than once: ");
      boolean needComma = false;
      for (Map.Entry<String,URL> entry: services.entrySet()) {
        if (needComma) {
          msg.append(", ");
        }
        msg.append(entry.getKey()).append(" in ").append(entry.getValue());
        needComma = true;
      }
      throw new TentackleRuntimeException(msg.toString());
    }
    return services.entrySet().iterator().next();
  }


  /**
   * Finds the service providers by service names.
   *
   * @param <T> the service type
   * @param service the service class
   * @return the classes providing this service
   * @throws ClassNotFoundException if some provider could not be loaded
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public synchronized <T> Collection<Class<T>> findServiceProviders(Class<T> service) throws ClassNotFoundException {
    String serviceName = service.getName();
    Collection<Class<T>> serviceImplClasses = (Collection) classMap.get(serviceName);
    if (serviceImplClasses == null) {
      serviceImplClasses = new ArrayList<>();
      for (Map.Entry<String,URL> entry: findServiceConfigurations(serviceName).entrySet()) {
        String serviceImplName = entry.getKey();
        Class<T> provider = (Class<T>) Class.forName(serviceImplName, true, getClassLoader(serviceName));
        // check that provides really implements service
        checkProvider(provider, service, entry.getValue());
        serviceImplClasses.add(provider);
      }
      classMap.put(serviceName, (Collection) serviceImplClasses);
    }
    return serviceImplClasses;
  }


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
  @SuppressWarnings("unchecked")
  @Override
  public <T> Class<T> findFirstServiceProvider(Class<T> service) throws ClassNotFoundException {
    String serviceName = service.getName();
    Map.Entry<String,URL> entry = findFirstServiceConfiguration(serviceName);
    Class<T> provider = (Class<T>) Class.forName(entry.getKey(), true, getClassLoader(serviceName));
    checkProvider(provider, service, entry.getValue());
    return provider;
  }


  /**
   * Finds the unique service provider by service class.<br>
   *
   * @param <T> the service type
   * @param service the service class
   * @return the provider class
   * @throws ClassNotFoundException if some provider could not be loaded
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> Class<T> findUniqueServiceProvider(Class<T> service) throws ClassNotFoundException {
    String serviceName = service.getName();
    Map.Entry<String,URL> entry = findUniqueServiceConfiguration(serviceName);
    Class<T> provider = (Class<T>) Class.forName(entry.getKey(), true, getClassLoader(serviceName));
    checkProvider(provider, service, entry.getValue());
    return provider;
  }


  /**
   * Creates a map of classnames to mapped services.
   * <p>
   * They key of the map is the service name.
   * The value is the name providing the service which comes first in the classpath.
   *
   * @param serviceName the service class
   * @return the map
   */
  @Override
  public synchronized Map<String, String> createNameMap(String serviceName) {
    Map<String, String> nameMap = new HashMap<>();

    for (Map.Entry<String, URL> entry :
            ServiceFactory.getServiceFinder(Constants.MAPPED_SERVICE_PATH).findServiceConfigurations(serviceName).entrySet()) {

      String configuration = entry.getKey();
      int ndx = StringHelper.indexAnyOf(configuration, ":=");
      if (ndx < 0) {
        throw new TentackleRuntimeException("invalid service configuration '" + configuration + "' in " + entry.getValue());
      }
      String key = StringHelper.parseString(configuration.substring(ndx + 1).trim());
      String value = configuration.substring(0, ndx).trim();
      nameMap.putIfAbsent(key, value);
    }

    return nameMap;
  }


  /**
   * Creates a map of names to all mapped services.<p>
   * They key of the map is the service name.
   * The value is a list of names providing the service ordered by discovery along the classpath.
   *
   * @param serviceName the service class
   * @return the map
   */
  @Override
  public synchronized Map<String, Set<String>> createNameMapToAll(String serviceName) {
    Map<String, Set<String>> nameMap = new HashMap<>();

    for (Map.Entry<String, URL> entry :
            ServiceFactory.getServiceFinder(Constants.MAPPED_SERVICE_PATH).findServiceConfigurations(serviceName).entrySet()) {

      String configuration = entry.getKey();
      List<String> parts = StringHelper.split(configuration, ":= ");
      if (parts.size() != 2) {
        throw new TentackleRuntimeException("invalid service configuration '" + configuration + "' in " + entry.getValue());
      }

      String key = parts.get(1);
      Set<String> serviceList = nameMap.computeIfAbsent(key, k -> new HashSet<>());
      serviceList.add(parts.get(0));
    }

    return nameMap;
  }


  /**
   * Gets the classloader for the given service name.
   *
   * @param serviceName the service name
   * @return the loader, never null
   */
  protected ClassLoader getClassLoader(String serviceName) {
    ClassLoader cl = ServiceFactory.getExplicitClassLoader(servicePath, serviceName);
    if (cl == null) {
      cl = getClassLoader();
    }
    return cl;
  }

  /**
   * Parses a service URL.<br>
   * The implementation assumes the provider configuration file in property-file format.
   * This includes the special case of key-only properties which is equivalent to
   * the format used in {@link java.util.ServiceLoader}.
   *
   * @param url the url of the provider definition
   * @param serviceName the name of the service
   * @param services the implementing services map
   */
  protected void parseURL(URL url, String serviceName, Map<String, URL> services) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = normalizeLine(line);
        if (line != null) {
          URL otherUrl = services.put(line, url);
          if (otherUrl != null) {
            // check if urls end with the same service-path. This may be the case in testing environments (as in IDEs)
            String urlText = url.toString();
            String otherUrlText = otherUrl.toString();
            int urlNdx = urlText.lastIndexOf(servicePath);
            int otherUrlNdx = otherUrlText.lastIndexOf(servicePath);
            if (urlNdx > 0 && otherUrlNdx > 0 &&
                urlText.endsWith(otherUrlText.substring(otherUrlNdx))) {
              continue;   // this is okay
            }
            throw new TentackleRuntimeException(
                    "service " + serviceName + " defined more than once! (" + otherUrl + " and " + url + ")");
          }
        }
      }
    }
    catch (IOException iox) {
      throw new TentackleRuntimeException("error reading configuration from " + url, iox);
    }
  }

  /**
   * Parses an enumeration of URLs.
   *
   * @param urls the URLs
   * @param serviceName the service name
   * @param services the destination map
   */
  protected void parseURLs(Collection<URL> urls, String serviceName, Map<String, URL> services) {
    urls.forEach(url -> parseURL(url, serviceName, services));
  }

  /**
   * Normalizes a line.<br>
   * Trailing and leading spaces are ignored.
   * Comments starting with a hash # are stripped.
   * A hash can be escaped by a backslash.
   *
   * @param line the source line
   * @return the normalized line, null if line contains no information
   */
  protected String normalizeLine(String line) {
    boolean quoteFound = false;
    StringBuilder buf = new StringBuilder();
    for (int i=0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (quoteFound) {
        if (c != '#') {   // quoted something else, not \#
          buf.append('\\');
        }
        buf.append(c);
        quoteFound = false;
      }
      else {
        if (c == '\\') {
          quoteFound = true;
        }
        else {
          if (c == '#') {
            break;    // ignore comment
          }
          buf.append(c);
        }
      }
    }
    line = buf.toString().trim();
    return line.isEmpty() ? null : line;
  }


  /**
   * Checks that the provider really implements the given service.
   *
   * @param provider the provider
   * @param service the service
   * @param url the url
   */
  private void checkProvider(Class<?> provider, Class<?> service, URL url) {
    if (!service.isAssignableFrom(provider)) {
      throw new TentackleRuntimeException(
              "provider '" + provider + "' does not implement '" + service + "', configured in " + url);
    }
  }

}
