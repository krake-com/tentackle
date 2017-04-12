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

import java.util.Map;
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

    if (Constants.SERVICE_FACTORY_CLASSNAME != null) {
      try {
        @SuppressWarnings("unchecked")
        Class<? extends ServiceFactory> clazz = (Class<? extends ServiceFactory>) (
          Constants.SERVICE_FACTORY_CLASSLOADER == null ?
            Class.forName(Constants.SERVICE_FACTORY_CLASSNAME) :
            Class.forName(Constants.SERVICE_FACTORY_CLASSNAME, true, Constants.SERVICE_FACTORY_CLASSLOADER));

        return clazz.newInstance();
      }
      catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
        LOGGER.log(Level.WARNING,
                "creating instance of Constants.SERVICE_FACTORY_CLASSNAME failed -> trying ServiceLoader...",
                ex);
      }
    }

    try {
      ServiceLoader<ServiceFactory> loader = Constants.SERVICE_FACTORY_CLASSLOADER == null ?
              ServiceLoader.load(ServiceFactory.class) :
              ServiceLoader.load(ServiceFactory.class, Constants.SERVICE_FACTORY_CLASSLOADER);
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




  // the bootstrap finder (by default finds an instance of iself, see @Service annotation in DefaultServiceFinder)
  private Class<? extends ServiceFinder> bootstrapFinderClass;


  // map of finders for different classloaders and service paths
  private final Map<ServiceFinderKey,ServiceFinder> finderMap;

  /**
   * Use another classloader, if set.<br>
   * Default is to use Thread.currentThread().getContextClassLoader().
   */
  private ClassLoader classLoader;

  /**
   * Creates the factory.
   */
  public ServiceFactory() {
    try {
      // bootstrap finder used to load the effective finder class
      bootstrapFinderClass = new DefaultServiceFinder().findFirstServiceProvider(ServiceFinder.class);
    }
    catch (Exception ex) {
      // we cannot use LOGGER here because this may use the createService as well
      java.util.logging.Logger.getLogger(ServiceFactory.class.getName()).log(java.util.logging.Level.WARNING,
              "{0} -> fallback to {1}", new Object[]{ex.getMessage(), DefaultServiceFinder.class.getName()});
      bootstrapFinderClass = DefaultServiceFinder.class;
    }

    finderMap = new ConcurrentHashMap<>();
  }


  /**
   * Gets the fixed classloader.
   *
   * @return the classloader, null if default
   */
  public ClassLoader getClassLoader() {
    return classLoader;
  }

  /**
   * Sets the fixed classloader.<br>
   * Default is Thread.currentThread().getContextClassLoader().
   *
   * @param classLoader the classloader, null if default
   */
  public void setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
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
   * Gets the class of the bootstrap finder.
   *
   * @return the class of the bootstrap finder, never null
   */
  protected Class<? extends ServiceFinder> getBootstrapFinderClass() {
    return bootstrapFinderClass;
  }


  /**
   * Gets a service finder for a given classloader and service path.<br>
   * If the finder does not exist yet, it will be created.
   *
   * @param loader the classloader
   * @param servicePath the service path prefix
   * @return the finder
   */
  protected ServiceFinder getServiceFinderImpl(ClassLoader loader, String servicePath) {
    ServiceFinderKey key = new ServiceFinderKey(loader, servicePath);
    ServiceFinder finder = getFinderMap().get(key);
    if (finder == null) {
      try {
        finder = getBootstrapFinderClass().getConstructor(ClassLoader.class, String.class).newInstance(loader, servicePath);
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
   * @return the finder
   */
  protected ServiceFinder getServiceFinderImpl(String servicePath) {
    ClassLoader loader = classLoader;
    if (loader == null) {
      loader = Thread.currentThread().getContextClassLoader();
    }
    if (loader == null) {
      loader = getClass().getClassLoader();
    }
    return getServiceFinderImpl(loader, servicePath);
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
   * @return the finder
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

}
