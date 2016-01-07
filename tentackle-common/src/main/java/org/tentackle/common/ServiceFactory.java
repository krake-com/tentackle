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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for services based on {@link ServiceFinder}.<br>
 * This is the only factory that cannot be replaced by a {@code @Service}-annotation.
 *
 * @author harald
 */
public class ServiceFactory {

  // the singleton instance
  private static final ServiceFactory INSTANCE = new ServiceFactory();


  // the bootstrap finder (by default finds an instance of iself, see @Service annotation in DefaultServiceFinder)
  private Class<? extends ServiceFinder> serviceFinderClass;


  // hash key for the finder map
  private static final class Key {

    private final ClassLoader loader;
    private final String servicePath;

    private Key(ClassLoader loader, String servicePath) {
      this.loader = loader;
      this.servicePath = servicePath;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final Key other = (Key) obj;
      if (this.loader != other.loader && (this.loader == null || !this.loader.equals(other.loader))) {
        return false;
      }
      return !((this.servicePath == null) ? (other.servicePath != null) : !this.servicePath.equals(other.servicePath));
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 89 * hash + (this.loader != null ? this.loader.hashCode() : 0);
      hash = 89 * hash + (this.servicePath != null ? this.servicePath.hashCode() : 0);
      return hash;
    }

  }

  // map of finders for different classloaders and service paths
  private final Map<Key,ServiceFinder> finderMap;



  /**
   * Singleton.
   */
  private ServiceFactory() {
    try {
      // bootstrap finder used to load the effective finder class
      serviceFinderClass = new DefaultServiceFinder().findFirstServiceProvider(ServiceFinder.class);
    }
    catch (Exception ex) {
      // we cannot use LOGGER here because this may use the createService as well
      java.util.logging.Logger.getLogger(ServiceFactory.class.getName()).log(java.util.logging.Level.WARNING,
              "{0} -> fallback to {1}", new Object[]{ex.getMessage(), DefaultServiceFinder.class.getName()});
      serviceFinderClass = DefaultServiceFinder.class;
    }

    finderMap = new ConcurrentHashMap<>();
  }



  /**
   * Gets a service finder for a given classloader and service path.<br>
   * If the finder does not exist yet, it will be created.
   *
   * @param loader the classloader
   * @param servicePath the service path prefix
   * @return the finder
   */
  private ServiceFinder getServiceFinderImpl(ClassLoader loader, String servicePath) {
    if (loader == null) {
      throw new IllegalArgumentException("loader must not be null");
    }
    if (servicePath == null) {
      throw new IllegalArgumentException("service path must not be null");
    }
    Key key = new Key(loader, servicePath);
    ServiceFinder finder = finderMap.get(key);
    if (finder == null) {
      try {
        finder = serviceFinderClass.getConstructor(ClassLoader.class, String.class).newInstance(loader, servicePath);
        finderMap.put(key, finder);
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
   * The classloader used is {@code Thread.currentThread().getContextClassLoader()}.
   *
   * @param servicePath the service path prefix
   * @return the finder
   */
  private ServiceFinder getServiceFinderImpl(String servicePath) {
    return getServiceFinderImpl(Thread.currentThread().getContextClassLoader(), servicePath);
  }


  /**
   * Gets a service finder.<br>
   * If the finder does not exist yet, it will be created.
   * The classloader used is {@code Thread.currentThread().getContextClassLoader()}
   * and the service path is {@code "META_INF/services/"}.
   *
   * @return the finder
   */
  private ServiceFinder getServiceFinderImpl() {
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
