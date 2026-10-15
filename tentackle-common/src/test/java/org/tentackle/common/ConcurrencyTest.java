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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;

/**
 * Tests the thread-safety of the shared state in tentackle-common.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ConcurrencyTest {

  private static final int THREADS = 16;

  /**
   * Runs the given task concurrently in all threads, all starting at the same time.
   *
   * @param task the task, gets the thread number
   * @return the results in thread order
   */
  private static <T> List<T> runConcurrently(TaskFactory<T> task) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        Callable<T> callable = task.create(i);
        futures.add(executor.submit(() -> {
          start.await();
          return callable.call();
        }));
      }
      start.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(30, TimeUnit.SECONDS));
      }
      return results;
    }
    finally {
      executor.shutdownNow();
    }
  }

  @FunctionalInterface
  private interface TaskFactory<T> {
    Callable<T> create(int threadNo);
  }


  @Test
  public void encryptedPropertiesIgnoreCase() throws Exception {
    EncryptedProperties properties = new EncryptedProperties();
    int keysPerThread = 500;
    runConcurrently(threadNo -> () -> {
      for (int i = 0; i < keysPerThread; i++) {
        String key = "Key_" + threadNo + "_" + i;
        properties.setProperty(key, key);
        // also triggers the lazy creation of the case map concurrently with setProperty
        assertEquals(properties.getPropertyIgnoreCase(key.toLowerCase()), key);
      }
      return null;
    });

    for (int threadNo = 0; threadNo < THREADS; threadNo++) {
      for (int i = 0; i < keysPerThread; i++) {
        String key = "Key_" + threadNo + "_" + i;
        assertEquals(properties.getPropertyIgnoreCase(key.toUpperCase()), key);
      }
    }
  }

  @Test
  public void sameServiceFinderForAllThreads() throws Exception {
    ClassLoader loader = getClass().getClassLoader();
    // unique service path, so that the finder isn't cached yet
    String servicePath = "META-INF/concurrency-test-" + System.nanoTime() + "/";
    List<ServiceFinder> finders = runConcurrently(threadNo -> () -> ServiceFactory.getServiceFinder(loader, servicePath));
    for (ServiceFinder finder : finders) {
      assertSame(finder, finders.getFirst());
    }
  }

  @Test
  public void concurrentServiceLookups() throws Exception {
    // new finder with empty caches
    DefaultServiceFinder finder = new DefaultServiceFinder(getClass().getClassLoader(), null);
    List<Collection<Class<Cryptor>>> providers = runConcurrently(threadNo -> () -> finder.findServiceProviders(Cryptor.class));
    for (Collection<Class<Cryptor>> classes : providers) {
      assertEquals(classes, List.of(TestCryptor.class));
    }

    Collection<Class<Cryptor>> classes = finder.findServiceProviders(Cryptor.class);
    assertThrows(UnsupportedOperationException.class, classes::clear);
    Map<String, URL> configurations = finder.findServiceConfigurations(Cryptor.class.getName());
    assertThrows(UnsupportedOperationException.class, configurations::clear);
  }

}
