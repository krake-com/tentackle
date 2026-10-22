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

package org.tentackle.misc;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;


interface PropertiesUtilitiesHolder {
  PropertiesUtilities INSTANCE = ServiceFactory.createService(PropertiesUtilities.class, PropertiesUtilities.class);
}


/**
 * Utility methods for {@link java.util.Properties}.<br>
 * Properties are loaded via {@link PropertiesResource}s. Implementations of such
 * resource loaders must be annotated with &#64;{@link PropertiesResourceService}.
 * <p>
 * By default, the {@link DefaultPropertiesResource} is the only one. However, applications
 * can register more loaders, for example to override properties via environment variables
 * or use a YAML file instead.
 * <p>
 * Replaceable via &#64;{@link Service}.
 */
@Service(PropertiesUtilities.class)   // defaults to self
public class PropertiesUtilities {

  private static final Logger LOGGER = Logger.get(PropertiesUtilities.class);

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static PropertiesUtilities getInstance() {
    return PropertiesUtilitiesHolder.INSTANCE;
  }

  private record PrioritizedResource(PropertiesResource resource, int priority, boolean stopIfFound) implements Comparable<PrioritizedResource> {

    @Override
    public int compareTo(PrioritizedResource o) {
      int rv = Integer.compare(priority, o.priority);   // lower comes first
      if (rv == 0) {
        // same prio? order by name to make it predictable
        rv = resource.getClass().getName().compareTo(o.resource.getClass().getName());
      }
      return rv;
    }
  }

  private final Set<PrioritizedResource> prioritizedResources = new TreeSet<>();

  /**
   * Creates the utilities and loads all {@link PropertiesResource}s sorted by priority.
   *
   * @throws TentackleRuntimeException if no resource was found or a resource could not be instantiated
   */
  public PropertiesUtilities() {
    try {
      Collection<Class<PropertiesResource>> serviceProviders = ServiceFactory.getServiceFinder().findServiceProviders(PropertiesResource.class);
      int defaultPriority = serviceProviders.size() * 100;
      for (Class<PropertiesResource> resourceClass : serviceProviders) {
        if (!PropertiesResource.class.isAssignableFrom(resourceClass)) {
          throw new TentackleRuntimeException(resourceClass.getName() + " is not a PropertiesResource");
        }
        PropertiesResourceService annotation = resourceClass.getAnnotation(PropertiesResourceService.class);
        int priority = annotation == null || annotation.priority() == 0 ? defaultPriority : annotation.priority();
        boolean stopIfFound = annotation != null && annotation.stopIfNotFound();
        prioritizedResources.add(new PrioritizedResource(resourceClass.getConstructor().newInstance(), priority, stopIfFound));
        LOGGER.info("properties resource {0} loaded with priority {1}", resourceClass.getName(), priority);
        defaultPriority -= 100;
      }
      if (prioritizedResources.isEmpty()) {
        throw new TentackleRuntimeException("no PropertiesResources found");
      }
    }
    catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
           InvocationTargetException e) {
      throw new TentackleRuntimeException("loading properties resources failed", e);
    }
  }

  /**
   * Creates empty properties.
   *
   * @return the empty properties
   */
  public EncryptedProperties create() {
    return new EncryptedProperties();
  }

  /**
   * Creates empty properties.
   *
   * @param initialCapacity the number of elements to accommodate initially
   * @return the empty properties
   */
  public EncryptedProperties create(int initialCapacity) {
    return new EncryptedProperties(initialCapacity);
  }

  /**
   * Loads the properties for the given resource name.<br>
   * All {@link PropertiesResource}s are applied in the order of their priority,
   * so that resources with a higher priority override those with a lower one.
   *
   * @param name the resource name
   * @return the properties, null if no resource applied any properties
   */
  public EncryptedProperties loadIfExists(String name) {
    EncryptedProperties properties = create();
    properties.setName(name);
    boolean someApplied = false;    // true if at least one resource loaded
    for (PrioritizedResource prioritizedResource : prioritizedResources) {
      boolean applied = prioritizedResource.resource.apply(name, properties);
      if (applied) {
        someApplied = true;
        if (prioritizedResource.stopIfFound()) {
          LOGGER.fine("loading properties stopped after {0}", prioritizedResource.resource.getClass().getName());
          break;
        }
      }
    }
    return someApplied ? properties : null;
  }

  /**
   * Loads the properties for the given resource name.<br>
   * Same as {@link #loadIfExists(String)}, but throws a {@link FileNotFoundException}
   * if not found.
   *
   * @param name the resource name
   * @return the properties
   * @throws FileNotFoundException if no resource applied any properties
   */
  public EncryptedProperties load(String name) throws FileNotFoundException {
    EncryptedProperties properties = loadIfExists(name);
    if (properties == null) {
      throw new FileNotFoundException("properties resource " + name + " not found");
    }
    return properties;
  }

  /**
   * Applies the environment to given properties.
   * <p>
   * For security reasons, only already existing properties will be overridden.
   * Property keys not starting with a letter are ignored.<br>
   * An optional prefix prepends the environment variable names derived from the property names.
   *
   * @param properties the properties to override
   * @param envPrefix optional prefix, null if none
   * @return true if properties were overridden
   * @see #toEnvironmentVariable(String)
   */
  public boolean applyEnvironment(Properties properties, String envPrefix) {
    boolean applied = false;
    for (String propertyName : properties.stringPropertyNames()) {
      if (!propertyName.isEmpty() && Character.isLetter(propertyName.charAt(0))) {
        String envName = toEnvironmentVariable(propertyName);
        if (envPrefix != null) {
          envName = envPrefix + envName;
        }
        String value = System.getenv(envName);
        if (value != null) {
          Object oldValue = properties.setProperty(propertyName, value);
          LOGGER.fine("property {0} overridden by environment variable {1}: {2} -> {3}", propertyName, envName, oldValue, value);
          applied = true;
        }
      }
    }
    return applied;
  }

  /**
   * Converts a property key to an environment variable name.
   * <p>
   * The following naming-rules apply:
   * <ul>
   *   <li>dots are converted to underscores</li>
   *   <li>all characters other than letters or numbers are removed</li>
   *   <li>the remaining characters will be converted to uppercase</li>
   * </ul>
   * <pre>
   *   Examples:
   *   my-Url -> MYURL
   *   tentackle.url -> TENTACKLE_URL
   * </pre>
   *
   * @param property the property key
   * @return the environment variable name
   */
  public String toEnvironmentVariable(String property) {
    StringBuilder builder = new StringBuilder();
    int len = property.length();
    for (int i = 0; i < len; i++) {
      char c = property.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        builder.append(c);
      }
      else if (c == '.') {
        builder.append('_');
      }
    }
    return builder.toString().toUpperCase(Locale.ROOT);
  }

}
