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

import org.testng.annotations.Test;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Tests the caching of resolved service providers and the explicit classloaders.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ServiceProviderCacheTest {

  /**
   * Finder counting the configuration lookups with a switchable classloader.
   */
  private static class CountingFinder extends DefaultServiceFinder {

    private final AtomicInteger firstLookups = new AtomicInteger();
    private final AtomicInteger uniqueLookups = new AtomicInteger();
    private volatile ClassLoader serviceLoader;

    CountingFinder(ClassLoader loader) {
      super(loader, Constants.DEFAULT_SERVICE_PATH);
      serviceLoader = loader;
    }

    @Override
    public Map.Entry<String, URL> findFirstServiceConfiguration(String serviceName) {
      firstLookups.incrementAndGet();
      return super.findFirstServiceConfiguration(serviceName);
    }

    @Override
    public Map.Entry<String, URL> findUniqueServiceConfiguration(String serviceName) {
      uniqueLookups.incrementAndGet();
      return super.findUniqueServiceConfiguration(serviceName);
    }

    @Override
    protected ClassLoader getClassLoader(String serviceName) {
      return serviceLoader;
    }
  }

  /**
   * Creates a classloader that delegates everything to the given parent.
   *
   * @param parent the parent loader
   * @return a new loader, distinct from the parent
   */
  private static ClassLoader delegatingLoader(ClassLoader parent) {
    return new ClassLoader(parent) {};
  }


  @Test
  public void testProvidersAreCached() throws Exception {
    ClassLoader loader = getClass().getClassLoader();
    CountingFinder finder = new CountingFinder(loader);

    Class<StringNormalizer> first = finder.findFirstServiceProvider(StringNormalizer.class);
    assertSame(first, DefaultStringNormalizer.class);
    assertSame(finder.findFirstServiceProvider(StringNormalizer.class), first);
    assertEquals(finder.firstLookups.get(), 1);

    Class<StringNormalizer> unique = finder.findUniqueServiceProvider(StringNormalizer.class);
    assertSame(unique, DefaultStringNormalizer.class);
    assertSame(finder.findUniqueServiceProvider(StringNormalizer.class), unique);
    assertEquals(finder.uniqueLookups.get(), 1);
  }

  @Test
  public void testChangedClassLoaderInvalidatesCache() throws Exception {
    ClassLoader loader = getClass().getClassLoader();
    CountingFinder finder = new CountingFinder(loader);

    finder.findFirstServiceProvider(StringNormalizer.class);
    finder.findUniqueServiceProvider(StringNormalizer.class);
    assertEquals(finder.firstLookups.get(), 1);
    assertEquals(finder.uniqueLookups.get(), 1);

    // e.g., an OSGi bundle registering an explicit classloader
    finder.serviceLoader = delegatingLoader(loader);
    finder.findFirstServiceProvider(StringNormalizer.class);
    finder.findUniqueServiceProvider(StringNormalizer.class);
    assertEquals(finder.firstLookups.get(), 2);
    assertEquals(finder.uniqueLookups.get(), 2);
    finder.findFirstServiceProvider(StringNormalizer.class);
    finder.findUniqueServiceProvider(StringNormalizer.class);
    assertEquals(finder.firstLookups.get(), 2);
    assertEquals(finder.uniqueLookups.get(), 2);

    // bundle stopped
    finder.serviceLoader = loader;
    finder.findFirstServiceProvider(StringNormalizer.class);
    finder.findUniqueServiceProvider(StringNormalizer.class);
    assertEquals(finder.firstLookups.get(), 3);
    assertEquals(finder.uniqueLookups.get(), 3);
  }

  @Test
  public void testExplicitClassLoaders() {
    ServiceFactory factory = new ServiceFactory();
    String path = "META-INF/services/";
    String name = "some.Service";
    ClassLoader parent = getClass().getClassLoader();
    ClassLoader cl1 = delegatingLoader(parent);
    ClassLoader cl2 = delegatingLoader(parent);

    assertNull(factory.getExplicitClassLoaderImpl(path, name));

    factory.addExplicitClassLoaderImpl(path, name, cl1);
    assertSame(factory.getExplicitClassLoaderImpl(path, name), cl1);
    assertNull(factory.getExplicitClassLoaderImpl(path, "other.Service"));

    // most recently added first
    factory.addExplicitClassLoaderImpl(path, name, cl2);
    assertSame(factory.getExplicitClassLoaderImpl(path, name), cl2);

    // adding again moves to the front
    factory.addExplicitClassLoaderImpl(path, name, cl1);
    assertSame(factory.getExplicitClassLoaderImpl(path, name), cl1);

    // removing activates the previously added one
    factory.removeExplicitClassLoaderImpl(path, name, cl1);
    assertSame(factory.getExplicitClassLoaderImpl(path, name), cl2);

    // removing an unknown loader changes nothing
    factory.removeExplicitClassLoaderImpl(path, name, cl1);
    assertSame(factory.getExplicitClassLoaderImpl(path, name), cl2);

    factory.removeExplicitClassLoaderImpl(path, name, cl2);
    assertNull(factory.getExplicitClassLoaderImpl(path, name));
    factory.removeExplicitClassLoaderImpl(path, name, cl2);    // no such key anymore
    assertNull(factory.getExplicitClassLoaderImpl(path, name));
  }

}
