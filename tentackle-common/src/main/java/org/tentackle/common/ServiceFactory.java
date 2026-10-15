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
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

interface ServiceFactoryHolder {
  ServiceFactory INSTANCE = ServiceFactory.createServiceFactory();
}

/**
 * Factory for services based on {@link ServiceFinder}.
 * <p>
 * A replacement for {@link ServiceLoader} providing the following additional features:
 * <ul>
 * <li>Not limited to load classes. Can load any kind of configuration.</li>
 * <li>Supports different service types. Each type gets its own directory in META-INF.</li>
 * <li>Each service (type + name) may have its own classloader. Useful for OSGi.</li>
 * <li>Works for modular (jigsaw) and non-modular applications.</li>
 * <li>As opposed to {@link ServiceLoader}, {@link ServiceFinder}s retain the module dependency order for service URLs. The first
 * URL is guaranteed to belong to the topmost relevant module. Unnamed, i.e., classpath and automatic modules, come last
 * (unless the service is annotated with {@link ClasspathFirst}, see {@link ModuleSorter}).</li>
 * </ul>
 * Application modules must include an implementation of {@link ModuleHook} and a provides-clause
 * in module-info. Example:
 * <pre>
 *  provides org.tentackle.common.ModuleHook with com.myapp.somepackage.Hook;
 * </pre>
 * <p>
 * The factory itself is loaded via the standard JDK {@link ServiceLoader} and can
 * be replaced via {@code META-INF/services/org.tentackle.common.ServiceFactory} on the classpath or, in modular mode,
 * via a {@code provides org.tentackle.common.ServiceFactory with ...} clause in module-info
 * ({@link ServiceLoader} ignores {@code META-INF/services} within named modules).
 * Alternatively, see {@link #setFactoryClassname(String)}.
 * If no such configuration is found, an instance of {@link ServiceFactory} is used,
 * i.e., the fallback and default implementation <em>is</em> {@link ServiceFactory} itself.
 *
 * @author harald
 */
// @Service does not work in tentackle-common, since the analyze goal of the tentackle-maven-plugin is not available at this build step yet.
// Needs explicit config in src/main/resources/META-INF/services.
@Service(ServiceFactory.class)    // defaults to self
public class ServiceFactory {

  /**
   * the logger for this class.
   * We must use java.util logging because we cannot use the service factory yet.
   */
  private static final Logger LOGGER = Logger.getLogger(ServiceFactory.class.getName());


  private static volatile String factoryClassname;
  private static volatile ClassLoader factoryClassloader;

  /** true once the singleton has been created, i.e., the settings above are frozen. */
  private static volatile boolean factoryCreated;


  /**
   * Warns if the singleton has already been created.<br>
   * Changing the settings has no effect from then on.
   *
   * @param what the setting being changed
   */
  private static void assertFactoryNotCreated(String what) {
    if (factoryCreated) {
      LOGGER.log(Level.WARNING,
                 "the service factory has already been created -> setting the factory " + what + " has no effect",
                 new Throwable("stacktrace"));
    }
  }


  /**
   * Sets the classname of the service factory.
   * <p>
   * If this value is null, the {@link ServiceFactory} is loaded via the {@link ServiceLoader}.
   * However, if for whatever reason, the loader doesn't find the service configuration or cannot
   * be used, the class defined by {@code factoryClassname} can be instantiated.
   * Applications must change this at a very early stage of startup, for example in the main-method.
   * <p>
   * Notice that the singleton is created lazily on its first use. Setting the classname afterwards
   * has no effect and is logged as a warning.
   *
   * @param factoryClassname the classname
   */
  public static void setFactoryClassname(String factoryClassname) {
    assertFactoryNotCreated("classname");
    ServiceFactory.factoryClassname = factoryClassname;
  }

  /**
   * Gets the classname of the service factory.
   *
   * @return the classname, default is null
   */
  public static String getFactoryClassname() {
    return factoryClassname;
  }


  /**
   * Sets the classloader to load the service factory.
   * <p>
   * If this value is null, the {@link ServiceFactory} is loaded via a default classloader.
   * <p>
   * Notice that the singleton is created lazily on its first use. Setting the classloader afterwards
   * has no effect and is logged as a warning.
   *
   * @param factoryClassloader the classloader
   */
  public static void setFactoryClassloader(ClassLoader factoryClassloader) {
    assertFactoryNotCreated("classloader");
    ServiceFactory.factoryClassloader = factoryClassloader;
  }

  /**
   * Gets the classloader to load the service factory.
   *
   * @return the classloader, default is null
   */
  public static ClassLoader getFactoryClassloader() {
    return factoryClassloader;
  }


  /**
   * Creates the singleton via {@link ServiceLoader}.<br>
   * Invoked once, lazily, by {@link ServiceFactoryHolder}.
   *
   * @return the instance, never null (fallback to this class)
   */
  static ServiceFactory createServiceFactory() {

    factoryCreated = true;    // any further setFactoryXXX comes too late
    String classname = factoryClassname;
    ClassLoader classloader = factoryClassloader;

    if (classname != null) {
      try {
        @SuppressWarnings("unchecked")
        Class<? extends ServiceFactory> clazz = (Class<? extends ServiceFactory>) (
          classloader == null ?
            Class.forName(classname) :
            Class.forName(classname, true, classloader));

        return clazz.getDeclaredConstructor().newInstance();
      }
      catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
             InvocationTargetException | NoSuchMethodException ex) {
        LOGGER.log(Level.WARNING,
                "creating instance of " + classname + " failed -> trying ServiceLoader...",
                ex);
      }
    }

    try {
      ServiceLoader<ServiceFactory> loader = classloader == null ?
              ServiceLoader.load(ServiceFactory.class) :
              ServiceLoader.load(ServiceFactory.class, classloader);
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


  /**
   * Gets the singleton.
   * <p>
   * The instance is created lazily on the first invocation, so that
   * {@link #setFactoryClassname(String)} and {@link #setFactoryClassloader(ClassLoader)}
   * are effective if applied before.
   *
   * @return the singleton, never null
   */
  public static ServiceFactory getInstance() {
    return ServiceFactoryHolder.INSTANCE;
  }


  /**
   * Gets a service finder for a given classloader and service path.<br>
   * If the finder does not exist yet, it will be created.
   *
   * @param loader the classloader, never null
   * @param servicePath the service path prefix
   * @return the finder, never null
   * @throws NullPointerException if loader or servicePath is null
   */
  public static ServiceFinder getServiceFinder(ClassLoader loader, String servicePath) {
    return getInstance().getServiceFinderImpl(loader, servicePath);
  }


  /**
   * Gets a service finder looking up services within a module layer.<br>
   * If the finder does not exist yet, it will be created.
   * <p>
   * Unlike {@link #getServiceFinder(ClassLoader, String)}, this always creates a
   * {@link DefaultServiceFinder}, bypassing the pluggable {@link Service}-replaced finder class.
   * A replaced {@link ServiceFinder} implementation is never used for module layers.
   * <p>
   * Layer-bound finders are cached separately from the classloader-based finders, even if the
   * layer's classloader is the same (see {@link ServiceFinderKey}).
   *
   * @param layer the module layer
   * @param servicePath the service path prefix
   * @return the finder, never null
   */
  public static ServiceFinder getServiceFinder(ModuleLayer layer, String servicePath) {
    return getInstance().getServiceFinderImpl(layer, servicePath);
  }


  /**
   * Gets a service finder for a given service path.<br>
   * If the finder does not exist yet, it will be created.
   * The classloader to use is determined as follows:
   * <ol>
   * <li>use the fixed classloader set by {@link #setFixedClassLoader(java.lang.ClassLoader)}, if not null</li>
   * <li>else try {@code Thread.currentThread().getContextClassLoader()}</li>
   * <li>if the context classloader is null, use the classloader that loaded the service factory</li>
   * </ol>
   *
   * @param servicePath the service path prefix
   * @return the finder, never null
   */
  public static ServiceFinder getServiceFinder(String servicePath) {
    return getInstance().getServiceFinderImpl(servicePath);
  }


  /**
   * Gets a service finder for the service path {@code "META-INF/services/"}.<br>
   * If the finder does not exist yet, it will be created.
   * The classloader to use is determined as follows:
   * <ol>
   * <li>use the fixed classloader set by {@link #setFixedClassLoader(java.lang.ClassLoader)}, if not null</li>
   * <li>else try {@code Thread.currentThread().getContextClassLoader()}</li>
   * <li>if the context classloader is null, use the classloader that loaded the service factory</li>
   * </ol>
   *
   * @return the finder, never null
   */
  public static ServiceFinder getServiceFinder() {
    return getInstance().getServiceFinderImpl();
  }


  /**
   * Utility method to determine the provider class of a service.<br>
   * Finds the first service implementation in module/classpath order (see {@link ModuleSorter}).
   *
   * @param <T> the service type
   * @param serviceClass the service class
   * @return the provider class of the service
   * @throws TentackleRuntimeException if no provider found or the provider does not implement the service
   */
  public static <T> Class<T> createServiceClass(Class<T> serviceClass) {
    try {
      return getInstance().getServiceFinderImpl().findFirstServiceProvider(serviceClass);
    }
    catch (ClassNotFoundException ex) {
      throw new TentackleRuntimeException("cannot create service class for " + serviceClass, ex);
    }
  }


  /**
   * Utility method to create a service instance.<br>
   * This is the standard way to instantiate singletons.
   * Finds the first service implementation in module/classpath order (see {@link ModuleSorter})
   * and invokes its no-arg constructor.
   *
   * @param <T> the service type
   * @param serviceClass the service class
   * @return an instance of the service
   * @throws TentackleRuntimeException if no provider found or it could not be instantiated
   */
  public static <T> T createService(Class<T> serviceClass) {
    try {
      return createServiceClass(serviceClass).getDeclaredConstructor().newInstance();
    }
    catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
           InvocationTargetException ex) {
      throw new TentackleRuntimeException("cannot create instance for " + serviceClass, ex);
    }
  }


  /**
   * Utility method to create a service instance with a default if not found.
   *
   * @param <T> the service type
   * @param serviceClass the service class
   * @param defaultClass the fallback class if no service found or could not be instantiated,
   *                     null to rethrow the exception
   * @param logDefault true if log warning with stacktrace if default implementation is used
   * @return an instance of the service
   * @throws TentackleRuntimeException if the service could not be created and the default class is null
   *         or could not be instantiated either
   */
  public static <T> T createService(Class<T> serviceClass, Class<? extends T> defaultClass, boolean logDefault) {
    try {
      return createService(serviceClass);
    }
    catch (TentackleRuntimeException re) {
      if (defaultClass == null) {   // one never knows...
        throw re;
      }
      try {
        if (logDefault) {
          // we cannot use LOGGER here because this may use the createService as well
          java.util.logging.Logger.getLogger(ServiceFactory.class.getName()).log(
              java.util.logging.Level.WARNING,
              "creating service for " + serviceClass.getName() +
              " failed -> creating default " + defaultClass.getName(),
              re);
        }
        return defaultClass.getDeclaredConstructor().newInstance();
      }
      catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
             InvocationTargetException ex) {
        throw new TentackleRuntimeException("cannot create default instance " + defaultClass + " for " + serviceClass, ex);
      }
    }
  }

  /**
   * Utility method to create a service instance with a default if not found.<br>
   * Same as {@link #createService(Class, Class, boolean)} with a warning logged
   * if the default class is used.
   *
   * @param <T> the service type
   * @param serviceClass the service class
   * @param defaultClass the default class if no service found
   * @return an instance of the service
   * @throws TentackleRuntimeException if neither the service nor the default could be created
   */
  public static <T> T createService(Class<T> serviceClass, Class<? extends T> defaultClass) {
    return createService(serviceClass, defaultClass, true);
  }


  /**
   * Gets the classloader for a given service path and name.<br>
   * If an explicit classloader is set, the last defined classloader for the given path and name is returned.
   * Otherwise, the default classloader is returned.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @return the classloader, never null
   */
  public static ClassLoader getClassLoader(String servicePath, String serviceName) {
    return getInstance().getClassLoaderImpl(servicePath, serviceName);
  }


  /**
   * Gets the explicit classloader for a given service path and name.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @return the classloader, null if no explicit classloader set
   */
  public static ClassLoader getExplicitClassLoader(String servicePath, String serviceName) {
    // chicken/egg: may be invoked while the singleton itself is being created, in which case
    // the holder's field still reads as null. Don't use getInstance() here, it would recurse.
    ServiceFactory factory = ServiceFactoryHolder.INSTANCE;
    if (factory != null) {
      return factory.getExplicitClassLoaderImpl(servicePath, serviceName);
    }
    return null;
  }


  /**
   * Adds an explicit classloader for a given service path and name.<br>
   * Loaders are kept in a linked list for each path and name combination, the most recently added first.
   * Adding a classloader pushes it onto the list (moving it to the front, if already contained),
   * removing it deletes it from the list, wherever it is located.
   * Thus, removing the most recently added loader activates the previously added one, if any.
   * This allows activation and deactivation as is used in OSGi, for example.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @param classLoader the classloader to add
   */
  public static void addExplicitClassLoader(String servicePath, String serviceName, ClassLoader classLoader) {
    getInstance().addExplicitClassLoaderImpl(servicePath, serviceName, classLoader);
  }


  /**
   * Removes an explicit classloader for a given service path and name.<br>
   * Loaders are kept in a linked list for each path and name combination, the most recently added first.
   * Adding a classloader pushes it onto the list (moving it to the front, if already contained),
   * removing it deletes it from the list, wherever it is located.
   * Thus, removing the most recently added loader activates the previously added one, if any.
   * This allows activation and deactivation as is used in OSGi, for example.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @param classLoader the classloader to remove
   */
  public static void removeExplicitClassLoader(String servicePath, String serviceName, ClassLoader classLoader) {
    getInstance().removeExplicitClassLoaderImpl(servicePath, serviceName, classLoader);
  }


  /**
   * Applies a given resource index.<br>
   * The index is usually created via the tentackle-maven-plugin's analyze goal
   * with the index option set to something like {@code "META-INF/RESOURCE-INDEX.LIST"}.
   * It is a plain list of resource paths, one per line, such as
   * {@code META-INF/services/org.tentackle.common.ServiceFactory}. Lines starting with {@code #} are ignored.
   * For each entry, the given classloader is added as (or removed from) the explicit classloader
   * for the service path and name (see {@link #addExplicitClassLoader(String, String, ClassLoader)}).
   * If the classloader finds more than one index resource, only the first is used.
   * <p>
   * This is a utility method that can be used during the activation within an
   * OSGi bundle, for example.
   *
   * @param classLoader the classloader to load the index resource
   * @param indexName the name of the index resource
   * @param set true if set loaders, else remove loaders from the services index
   * @throws TentackleRuntimeException if reading the index failed
   */
  public static void applyResourceIndex(ClassLoader classLoader, String indexName, boolean set) {
    getInstance().applyResourceIndexImpl(classLoader, indexName, set);
  }




  // --------------------------- implementation goes here -------------------------------------

  private record ClassLoaderKey(String servicePath, String serviceName) {}      // key to maintain explicit classloaders

  private final Map<ClassLoaderKey, LinkedList<ClassLoader>> explicitLoaders;   // optional explicit loaders for named services


  /**
   * The class of the service finder.<br>
   * Determined via the {@link ServiceFinder} service configuration, which defaults to {@link DefaultServiceFinder}
   * (see the &#64;{@link Service} annotation in {@link DefaultServiceFinder}).
   */
  private Class<? extends ServiceFinder> serviceFinderClass;

  /**
   * map of finders for different classloaders and service paths.
   */
  private final Map<ServiceFinderKey,ServiceFinder> finderMap;

  /**
   * Use another classloader, if set.<br>
   * Default is to use the context classloader (see {@link #getClassLoader()}).
   */
  private volatile ClassLoader fixedClassLoader;


  /**
   * Creates the factory.
   */
  public ServiceFactory() {
    try {
      // service finder used to load the effective finder class
      serviceFinderClass = new DefaultServiceFinder().findFirstServiceProvider(ServiceFinder.class);
    }
    catch (ClassNotFoundException ex) {
      // we cannot use LOGGER here because this may use the createService as well
      java.util.logging.Logger.getLogger(ServiceFactory.class.getName()).log(java.util.logging.Level.INFO,
              "cannot load ServiceFinder class -> fallback to " + DefaultServiceFinder.class.getName(), ex);
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
   * If set, it is used instead of the context classloader for all finders that are not
   * created for an explicit classloader or module layer.
   * Finders already created are not affected.
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
   * <li>use the fixed classloader set by {@link #setFixedClassLoader(java.lang.ClassLoader)}, if not null</li>
   * <li>else try {@code Thread.currentThread().getContextClassLoader()}</li>
   * <li>if the context classloader is null, use the classloader that loaded the service factory</li>
   * </ol>
   *
   * @return the loader, never null
   */
  protected ClassLoader getClassLoader() {
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
   * If an explicit classloader is set, the last defined classloader for the given path and name is returned.
   * Otherwise, the default classloader is returned.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @return the classloader, never null
   */
  protected ClassLoader getClassLoaderImpl(String servicePath, String serviceName) {
    ClassLoader cl = getExplicitClassLoader(servicePath, serviceName);
    if (cl == null) {
      cl = getClassLoader();
    }
    return cl;
  }


  /**
   * Gets the explicit classloader for a given service path and name.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @return the classloader, null if no explicit classloader set
   */
  protected synchronized ClassLoader getExplicitClassLoaderImpl(String servicePath, String serviceName) {
    ClassLoader cl = null;
    LinkedList<ClassLoader> cList = explicitLoaders.get(new ClassLoaderKey(servicePath, serviceName));
    if (cList != null && !cList.isEmpty()) {
      cl = cList.getFirst();
    }
    return cl;
  }


  /**
   * Adds an explicit classloader for a given service path and name.<br>
   * Loaders are kept in a linked list for each path and name combination, the most recently added first.
   * Adding a classloader pushes it onto the list (moving it to the front, if already contained),
   * removing it deletes it from the list, wherever it is located.
   * Thus, removing the most recently added loader activates the previously added one, if any.
   * This allows activation and deactivation as is used in OSGi, for example.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @param classLoader the classloader to add
   */
  protected synchronized void addExplicitClassLoaderImpl(String servicePath, String serviceName, ClassLoader classLoader) {
    ClassLoaderKey key = new ClassLoaderKey(servicePath, serviceName);
    LinkedList<ClassLoader> loaders = explicitLoaders.computeIfAbsent(key, k -> new LinkedList<>());
    // remove previously added loader (-> Set)
    // if the same classloader is added twice, the cl is moved to the start of the list
    loaders.remove(classLoader);
    loaders.push(classLoader);
  }


  /**
   * Removes an explicit classloader for a given service path and name.<br>
   * Loaders are kept in a linked list for each path and name combination, the most recently added first.
   * Adding a classloader pushes it onto the list (moving it to the front, if already contained),
   * removing it deletes it from the list, wherever it is located.
   * Thus, removing the most recently added loader activates the previously added one, if any.
   * This allows activation and deactivation as is used in OSGi, for example.
   *
   * @param servicePath the service path
   * @param serviceName the service name
   * @param classLoader the classloader to remove
   */
  protected synchronized void removeExplicitClassLoaderImpl(String servicePath, String serviceName, ClassLoader classLoader) {
    ClassLoaderKey key = new ClassLoaderKey(servicePath, serviceName);
    LinkedList<ClassLoader> loaders = explicitLoaders.get(key);
    if (loaders != null) {
      loaders.remove(classLoader);
      if (loaders.isEmpty()) {
        explicitLoaders.remove(key);
      }
    }
  }


  /**
   * Applies a given resource index.<br>
   * The index is usually created via the tentackle-maven-plugin's analyze goal
   * with the index option set to something like {@code "META-INF/RESOURCE-INDEX.LIST"}.
   * It is a plain list of resource paths, one per line, such as
   * {@code META-INF/services/org.tentackle.common.ServiceFactory}. Lines starting with {@code #} are ignored.
   * For each entry, the given classloader is added as (or removed from) the explicit classloader
   * for the service path and name (see {@link #addExplicitClassLoader(String, String, ClassLoader)}).
   * If the classloader finds more than one index resource, only the first is used.
   * <p>
   * This is a utility method that can be used during the activation within an
   * OSGi bundle, for example.
   *
   * @param classLoader the classloader to load the index resource
   * @param indexName the name of the index resource
   * @param set true if set loaders, else remove loaders from the services index
   * @throws TentackleRuntimeException if reading the index failed
   */
  protected void applyResourceIndexImpl(ClassLoader classLoader, String indexName, boolean set) {
    try {
      Enumeration<URL> urls = classLoader.getResources(indexName);
      if (urls.hasMoreElements()) {
        // we take only the first
        URL url = urls.nextElement();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(FileHelper.openStreamUncached(url)))) {
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
              if (set) {
                addExplicitClassLoader(servicePath, serviceName, classLoader);
              }
              else {
                removeExplicitClassLoader(servicePath, serviceName, classLoader);
              }
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
   * The map is thread-safe.
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
    ServiceFinder finder = getFinderMap().get(key);   // fast path without locking the bin
    if (finder == null) {
      // atomically, so that concurrent callers share the same finder and its caches
      finder = getFinderMap().computeIfAbsent(key, k -> {
        try {
          return getServiceFinderClass().getConstructor(ClassLoader.class, String.class).newInstance(loader, servicePath);
        }
        catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
               InvocationTargetException ex) {
          throw new TentackleRuntimeException("cannot instantiate service finder", ex);
        }
      });
    }
    return finder;
  }

  /**
   * Gets a service finder looking up services within a module layer.<br>
   * If the finder does not exist yet, it will be created.
   *
   * @param layer the module layer
   * @param servicePath the service path prefix
   * @return the finder, never null
   */
  protected ServiceFinder getServiceFinderImpl(ModuleLayer layer, String servicePath) {
    ServiceFinderKey key = new ServiceFinderKey(DefaultServiceFinder.resolveLayerLoader(layer), layer, servicePath);
    return getFinderMap().computeIfAbsent(key, k -> new DefaultServiceFinder(layer, servicePath));
  }

  /**
   * Gets a service finder for a given service path.<br>
   * If the finder does not exist yet, it will be created.
   * The classloader to use is determined as follows:
   * <ol>
   * <li> use the fixed classloader set by {@link #setFixedClassLoader(java.lang.ClassLoader)}, if not null</li>
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
   * Gets a service finder for the service path {@code "META-INF/services/"}.<br>
   * If the finder does not exist yet, it will be created.
   * The classloader is determined by {@link #getClassLoader()}.
   *
   * @return the finder, never null
   */
  protected ServiceFinder getServiceFinderImpl() {
    return getServiceFinderImpl(Constants.DEFAULT_SERVICE_PATH);
  }

}
