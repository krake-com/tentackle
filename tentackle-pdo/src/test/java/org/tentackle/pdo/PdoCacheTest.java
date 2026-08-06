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

package org.tentackle.pdo;

import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Unit tests for {@link PdoCache}.
 * <p>
 * A cache serves all threads of the application, so its state must be either guarded by its monitor
 * or volatile. Exercising the cache itself needs PDOs with a persistence delegate and a database and
 * is therefore left to the database-backed tests.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PdoCacheTest {

  /**
   * Gets a declared field of {@link PdoCache}.
   *
   * @param fieldName the field name
   * @return the field
   */
  private Field cacheField(String fieldName) {
    try {
      return PdoCache.class.getDeclaredField(fieldName);
    }
    catch (NoSuchFieldException nsx) {
      fail("no such field: " + fieldName, nsx);
      throw new AssertionError(nsx);    // not reached
    }
  }


  /**
   * The configuration of a cache must be volatile.
   * <p>
   * It is read and written through accessors that do not all synchronize on the cache - and is read
   * from application threads at any time - so without volatile a reconfiguration may go unnoticed by
   * the threads actually using the cache.
   */
  @Test
  public void testConfigurationFieldsAreVolatile() {
    for (String fieldName : List.of("allEnabled", "enabled", "strategy",
                                    "maxSize", "maxSizeHardLimit", "keepQuota")) {
      Field field = cacheField(fieldName);
      assertTrue(Modifier.isVolatile(field.getModifiers()),
                 field.getName() + " is part of the cache configuration and must be volatile");
    }
  }

  /**
   * The statistics counters, in contrast, are deliberately <em>not</em> volatile.
   * <p>
   * They are only ever read and written while holding the monitor, so making them volatile would
   * merely suggest that they may be touched without it.
   */
  @Test
  public void testStatisticCountersAreGuardedByTheMonitorInstead() {
    for (String fieldName : List.of("selectCount", "selectMissingCount",
                                    "selectNotCacheableCount", "selectAllCount")) {
      Field field = cacheField(fieldName);
      assertFalse(Modifier.isVolatile(field.getModifiers()),
                  field.getName() + " is only accessed under the monitor and needs no volatile");
    }
  }

  /**
   * The operations touching the indexes must hold the cache monitor.
   * <p>
   * {@link PdoCacheIndex} carries no synchronization of its own - the cache monitor is the only
   * thing protecting the indexes - so every method walking or modifying them must be synchronized.
   * {@code remove} and {@code shrinkCache} are protected and meant to be used by applications and
   * subclasses, which have no other way to lock the cache.
   */
  @Test
  public void testIndexTouchingMethodsAreSynchronized() {
    for (String methodName : List.of("remove", "shrinkCache", "getIndexes", "addIndex", "removeIndex")) {
      Method method = null;
      for (Method candidate : PdoCache.class.getDeclaredMethods()) {
        if (candidate.getName().equals(methodName)) {
          method = candidate;
          break;
        }
      }
      if (method == null) {
        fail("no such method: " + methodName);
      }
      assertTrue(Modifier.isSynchronized(method.getModifiers()),
                 methodName + "() walks or modifies the indexes and must be synchronized");
    }
  }

  /**
   * The cached select-all list must be replaceable.
   * <p>
   * {@code selectAll} hands the list out wrapped in a {@code CopyOnWriteList}, which copies only on
   * write - reading goes to the list itself. A refresh must therefore build a new list rather than
   * modify the published one, which requires the field to be non-final.
   */
  @Test
  public void testSelectAllListIsReplaceable() {
    for (Class<?> nested : PdoCache.class.getDeclaredClasses()) {
      if ("SelectAllList".equals(nested.getSimpleName())) {
        try {
          Field field = nested.getDeclaredField("list");
          assertFalse(Modifier.isFinal(field.getModifiers()),
                      "the list must be replaceable by a copy before a refresh modifies it");
          return;
        }
        catch (NoSuchFieldException nsx) {
          fail("no list field in SelectAllList", nsx);
        }
      }
    }
    fail("no SelectAllList class in " + PdoCache.class.getName());
  }

}
