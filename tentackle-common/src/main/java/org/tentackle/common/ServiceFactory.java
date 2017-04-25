/**
 * Tentackle - http://www.tentackle.org
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory for services based on {@link ServiceFinder}.
 * <p>
 * The factory itself is loaded via the standard JDK {@link ServiceLoader} and can
 * be replaced via META-INF/services. If no such configuration is found, for whatever reason,
 * an instance of {@link ServiceFactory} is used.
 * As a consequence, the fallback and default implementation <em>is</em> {@link ServiceFactory} itself.
 * However, applications can replace it, if necessary.
 *
 * @author harald
 */
public class ServiceFactory {

  /**
   * the logger for this class.
   * We must use java.util logging because we cannot use the service factory yet.
   */
  private static final Logger LOGGER = Logger.getLogger(ServiceFactory.class.getName());

  /**
   * Creates the singleton via {@link ServiceLoader}.
   *
   * @return the instance, never null (fallback to this class)
   */
  private static ServiceFactory createServiceFactory() {

    if (Settings.SERVICE_FACTORY_CLASSNAME != null) {
      try {
        @SuppressWarnings("unchecked")
        Class<? extends ServiceFactory> clazz = (Class<? extends ServiceFactory>) (
          Settings.SERVICE_FACTORY_CLASSLOADER == null ?
            Class.forName(Settings.SERVICE_FACTORY_CLASSNAME) :
            Class.forName(Settings.SERVICE_FACTORY_CLASSNAME, true, Settings.SERVICE_FACTORY_CLASSLOADER));

        return clazz.newInstance();
      }
      catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
        LOGGER.log(Level.WARNING,
                "creating instance of Constants.SERVICE_FACTORY_CLASSNAME failed -> trying ServiceLoader...",
                ex);
      }
    }

    try {
      ServiceLoader<ServiceFactory> loader = Settings.SERVICE_FACTORY_CLASSLOADER == null ?
              ServiceLoader.load(ServiceFactory.class) :
              ServiceLoader.load(ServiceFactory.class, Settings.SERVICE_FACTORY_CLASSLOADER);
      for (ServiceFactory factory: loader) {
        if (factory != null) {
          return factory;
        }
      }
    }
    catch (ServiceConfigurationError cx) {
      LOGGER.log(Level.WARNING,
                 "locating the service factory failed -> fallback to ServiceFactory",
                 cx);

    }

    return new ServiceFactory();
  }

  /** the singleton instance. */
  public static final ServiceFactory INSTANCE = createServiceFactory();


    /**
   * Gets a service finder for a given classloader and service path.<br>
   * If the finder does not exist yet, it will be created.
   *
   * @param loader the classloader
   * @param servicePath the service path prefix
   * @return the finder
   */
  public static ServiceFinder getServiceFinder(ClassLoader loader, String servicePath) {
    return INSTANCE.getServiceFinderImpl(loader, servicePath);
  }


  /**
   * Gets a service finder for a given service path.<br>
   * If the finder does not exist yet, it will be created.
   * The classloader used is {@code Thread.currentThread().getContextClassLoader()}.
   *
   * @param servicePath the service path prefix
   * @return the finder, never null
   */
  public static ServiceFinder getServiceFinder(String servicePath) {
    return INSTANCE.getServiceFinderImpl(servicePath);
  }


  /**
   * Gets a service finder.<br>
   * If the finder does not exist yet, it will be created.
   * The classloader used is {@code Thread.currentThread().getContextClassLoader()}
   * and the service path is {@code "META_INF/services/"}.
   *
   * @return the finder
   */
  public static ServiceFinder getServiceFinder() {
    return INSTANCE.getServiceFinderImpl();
  }


  /**
   * Utility method to create a service instance.<br>
   * This is the standard way to instantiate singletons.
   * Finds the first service implementation along the classpath.
   *
   * @param <T> the service type
   * @param serviceClass the service class
   * @return an instance of the service
   */
  public static <T> Class<T> createServiceClass(Class<T> serviceClass) {
    try {
      return INSTANCE.getServiceFinderImpl().findFirstServiceProvider(serviceClass);
    }
    catch (Exception ex) {
      throw new TentackleRuntimeException("cannot create service class for " + serviceClass, ex);
    }
  }


  /**
   * Utility method to create a service instance.<br>
   * This is the standard way to instantiate singletons.
   * Finds the first service implementation along the classpath.
   *
   * @param <T> the service type
   * @param serviceClass the service class
   * @return an instance of the service
   */
  public static <T> T createService(Class<T> serviceClass) {
    try {
      return createServiceClass(serviceClass).newInstance();
    }
    catch (Exception ex) {
      throw new TentackleRuntimeException("cannot create instance for " + serviceClass, ex);
    }
  }


  /**
   * Utility method to create a service instance with a default if not found.<br>
   *
   * @param <T> the service type
   * @param serviceClass the service class
   * @param defaultClass the default class if no service found
   * @return an instance of the service
   */
  public static <T> T createService(Class<T> serviceClass, Class<? extends T> defaultClass) {
    try {
      return createService(serviceClass);
    }
    catch (TentackleRuntimeException re) {
      try {
        // we cannot use LOGGER here because this may use the createService as well
        java.util.logging.Logger.getLogger(ServiceFactory.class.getName()).log(java.util.logging.Level.WARNING,
                "{0} -> creating default {1}", new Object[]{re.getMessage(), defaultClass.getName()});
        return defaultClass.newInstance();
      }
      catch (Exception ex) {
        throw new TentackleRuntimeException("cannot create default instance " + defaultClass + " for " + serviceClass, ex);
      }
    }
  }


  /**
   * Key to maintain explicit classloaders.
   */
  private static class ClassLoaderKey {
    private final String servicePath;
    private final String serviceName;

    public ClassLoaderKey(String servicePath, String serviceName) {
      this.servicePath = servicePath;
      this.serviceName = serviceName;
    }

    @Override
    public int hashCode() {
      int hash = 3;
      hash = 37 * hash + Objects.hashCode(this.servicePath);
      hash = 37 * hash + Objects.hashCode(this.serviceName);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final ClassLoaderKey other = (ClassLoaderKey) obj;
      if (!Objects.equals(this.servicePath, other.servicePath)) {
        return false;
      }
      return Objects.equals(this.serviceName, other.serviceName);
    }
  }


  private final Map<ClassLoaderKey,LinkedList<ClassLoader>> explicitLoaders;          // optional explicit loaders for named services


  /**
   * The service finder (by default finds an instance of iself.<br>
   * See @Service annotation in DefaultServiceFinder.
   */
  private Class<? extends ServiceFinder> serviceFinderClass;

  /**
   * map of finders for different classloaders and service paths.
   */
  private final Map<ServiceFinderKey,ServiceFinder> finderMap;

  /**
   * Use another classloader, if set.<br>
   * Default is to use Thread.currentThread().getContextClassLoader().
   */
  private ClassLoader fixedClassLoader;


  /**
   * Creates the factory.
   */
  public ServiceFactory() {
    try {
      // service finder used to load the effective finder class
      serviceFinderClass = new DefaultServiceFinder().findFirstServiceProvider(ServiceFinder.class);
    }
    catch (Exception ex) {
      // we cannot use LOGGER here because this may use the createService as well
      java.util.logging.Logger.getLogger(ServiceFactory.class.getName()).log(java.util.logging.Level.WARNING,
              "{0} -> fallback to {1}", new Object[]{ex.getMessage(), DefaultServiceFinder.class.getName()});
      serviceFinderClass = DefaultServiceFinder.class;
    }

    finderMap = new ConcurrentHashMap<>();
    explicitLoaders = new HashMap<>();
  }


  /**
   * Gets the fixed classloader.
   *
   * @return the classloader, null if default
   */
  public ClassLoader getFixedClassLoader() {
    return fixedClassLoader;
  }

  /**
   * Sets the fixed classloader.<br>
   * Default is Thread.currentThread().getContextClassLoader().
   *
   * @param classLoader the classloader, null if default
   */
  public void setFixedClassLoader(ClassLoader classLoader) {
    this.fixedClassLoader = classLoader;
  }


  /**
   * Gets the default classloader.<br>
   * The classloader to use is determined as follows:
   * <ol>
   * <li>use the fixed classloader set by {@link #setClassLoader(java.lang.ClassLoader)}, if not null</li>
   * <li>else try {@code Thread.currentThread().getContextClassLoader()}</li>
   * <li>if the context classloader is null, use the classloader that loaded the service factory</li>
   * </ol>
   *
   * @return the loader, never null
   */
  public ClassLoader getClassLoader() {
    ClassLoader loader = fixedClassLoader;
    if (loader == null) {
      loader = Thread.currentThread().getContextClassLoader();
    }
    if (loader == null) {
      loader = getClass().getClassLoader();
    }
    return loader;
  }


  /**
   * Gets the classloader for a given service path and name.<br>
   * If an explicit classloader is set, the last set classloader for the given path and name is returned.
   * Otherwise the default classloader is returned.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @return the classloader, never null
   */
  public synchronized ClassLoader getClassLoader(String servicePath, String serviceName) {
    ClassLoader cl;
    LinkedList<ClassLoader> cList = explicitLoaders.get(new ClassLoaderKey(servicePath, serviceName));
    if (cList != null && !cList.isEmpty()) {
      cl = cList.getFirst();
    }
    else {
      cl = getClassLoader();
    }
    return cl;
  }


  /**
   * Sets an explict classloader for a given service path and name.<br>
   * Loaders are kept in a linked list for each path and name combination.
   * Setting a classloader performs a push, clearing a pop.
   * Thus, removing a loader activates the previously set loader, if any.
   * This allows activation and deactivation as it used in OSGi, for example.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @param classLoader the classloader, null if remove
   */
  public synchronized void setClassLoader(String servicePath, String serviceName, ClassLoader classLoader) {
    ClassLoaderKey key = new ClassLoaderKey(servicePath, serviceName);
    LinkedList<ClassLoader> cList = explicitLoaders.get(key);
    if (classLoader == null) {
      if (cList != null) {
        if (!cList.isEmpty()) {
          cList.pop();
        }
        if (cList.isEmpty()) {
          explicitLoaders.remove(key);
        }
      }
    }
    else {
      if (cList == null) {
        cList = new LinkedList<>();
        explicitLoaders.put(key, cList);
      }
      cList.push(classLoader);
    }
  }


  /**
   * Apply a given resource index.<br>
   * The index is usually created via the tentackle-maven-plugin's analyze goal
   * with the index option set to something like {@code "META-INF/RESOURCES-INDEX.LIST"}.
   * <p>
   * This is a utility method, that can be used during the activation within an
   * OSGI-bundle, for example.
   *
   * @param classLoader the classloader to load the index resource
   * @param indexName the name of the index resource
   * @param set true if set loaders, else remove loaders from the services index
   */
  public void applyResourceIndex(ClassLoader classLoader, String indexName, boolean set) {
    try {
      Enumeration<URL> urls = classLoader.getResources(indexName);
      if (urls.hasMoreElements()) {
        // we take only the first
        URL url = urls.nextElement();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            if (!line.startsWith("#")) {
              int ndx = line.lastIndexOf('/');
              String servicePath;
              String serviceName;
              if (ndx > 0 && ndx < line.length() - 1) {
                servicePath = line.substring(0, ndx + 1);   // including trailing slash
                serviceName = line.substring(ndx + 1);
              }
              else {
                servicePath = "";
                serviceName = line;
              }
              setClassLoader(servicePath, serviceName, set ? classLoader : null);
            }
          }
        }
      }
    }
    catch (IOException ix) {
      throw new TentackleRuntimeException("applying index failed", ix);
    }
  }


  /**
   * Gets the finder map.<br>
   * Allows applications to modify or add finders programmatically.<br>
   * The map is threadsafe.
   *
   * @return the finder map
   */
  public Map<ServiceFinderKey,ServiceFinder> getFinderMap() {
    return finderMap;
  }


  /**
   * Gets the class of the service finder.
   *
   * @return the class of the service finder, never null
   */
  protected Class<? extends ServiceFinder> getServiceFinderClass() {
    return serviceFinderClass;
  }

  /**
   * Gets a service finder for a given classloader and service path.<br>
   * If the finder does not exist yet, it will be created.
   *
   * @param loader the classloader
   * @param servicePath the service path prefix
   * @return the finder, never null
   */
  protected ServiceFinder getServiceFinderImpl(ClassLoader loader, String servicePath) {
    ServiceFinderKey key = new ServiceFinderKey(loader, servicePath);
    ServiceFinder finder = getFinderMap().get(key);
    if (finder == null) {
      try {
        finder = getServiceFinderClass().getConstructor(ClassLoader.class, String.class).newInstance(loader, servicePath);
        getFinderMap().put(key, finder);
      }
      catch (Exception ex) {
        throw new TentackleRuntimeException("cannot instantiate service finder", ex);
      }
    }
    return finder;
  }

  /**
   * Gets a service finder for a given service path.<br>
   * If the finder does not exist yet, it will be created.
   * The classloader to use is determined as follows:
   * <ol>
   * <li> use the fixed classloader set by {@link #setClassLoader(java.lang.ClassLoader)}, if not null</li>
   * <li>else try {@code Thread.currentThread().getContextClassLoader()}</li>
   * <li>if the context classloader is null, use the classloader that loaded the service factory</li>
   * </ol>
   *
   * @param servicePath the service path prefix
   * @return the finder, never null
   */
  protected ServiceFinder getServiceFinderImpl(String servicePath) {
    return getServiceFinderImpl(getClassLoader(), servicePath);
  }

  /**
   * Gets a service finder.<br>
   * If the finder does not exist yet, it will be created.
   * The classloader used is {@code Thread.currentThread().getContextClassLoader()}
   * and the service path is {@code "META_INF/services/"}.
   *
   * @return the finder
   */
  protected ServiceFinder getServiceFinderImpl() {
    return getServiceFinderImpl(Constants.DEFAULT_SERVICE_PATH);
  }

}
