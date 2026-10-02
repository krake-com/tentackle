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

package org.tentackle.persist;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.dbms.DbModificationTracker;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.pdo.DefaultPdoCacheFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoCacheException;
import org.tentackle.pdo.PdoCacheFactory;
import org.tentackle.pdo.PdoCacheIndex;
import org.tentackle.pdo.PdoCacheStrategy;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.ModificationTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link PdoCache} against real PDOs.
 * <p>
 * The cache is fed from an in-memory storage instead of the database, so the tests can control
 * exactly which PDOs exist. The session, however, is real: the cache asks it whether a transaction is
 * running to detect cache pollution.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class PdoCacheDbTest extends AbstractPersistenceDbTest {

  private static final String TABLE_NAME = "democached";

  /**
   * A cache reading from memory.
   */
  private static class MemoryCache extends PdoCache<DemoCached> {

    private final Map<Long, DemoCached> storage = new TreeMap<>();    // the "database"
    private int selectAllCount;                                       // number of selectAllFromStorage invocations

    MemoryCache() {
      super(DemoCached.class, false);
    }

    @Override
    protected PdoCacheIndex<DemoCached, Long> createIdIndex() {
      return PdoCacheFactory.getInstance().createCacheIndex(
        "DemoCached:ID", (context, id) -> storage.get(id), pdo -> pdo.getPersistenceDelegate().getId());
    }

    @Override
    protected List<DemoCached> selectAllFromStorage(DomainContext context) {
      selectAllCount++;
      return new ArrayList<>(storage.values());
    }

    @Override
    protected boolean add(DemoCached pdo) {
      return super.add(pdo);
    }
  }


  private MemoryCache cache;

  @BeforeMethod
  public void createCache() {
    // the serial baseline of the table, so the tracker doesn't need to look it up in the database
    ((DbModificationTracker) ModificationTracker.getInstance()).setIdSerialForName(TABLE_NAME, new IdSerialTuple(1, 0));
    cache = new MemoryCache();
  }

  /**
   * Creates a PDO and puts it into the storage.
   *
   * @param id the object ID, also used as the tableserial
   * @return the PDO
   */
  private DemoCached store(long id) {
    DemoCached pdo = create(id);
    cache.storage.put(id, pdo);
    return pdo;
  }

  /**
   * Creates a PDO.
   *
   * @param id the object ID, also used as the tableserial
   * @return the PDO
   */
  private DemoCached create(long id) {
    DemoCached pdo = Pdo.create(DemoCached.class, getDomainContext());
    DemoCachedPersistenceImpl po = (DemoCachedPersistenceImpl) pdo.getPersistenceDelegate();
    po.setId(id);
    po.setTableSerial(id);
    return pdo;
  }


  /**
   * Selecting a PDO modified within the same transaction must not be repaired by the cache, but reach
   * the caller, so that the transaction is rolled back and the shared cache is not polluted.
   */
  @Test
  public void testPollutionInSelectAllIsNotRetried() {
    store(1);
    store(2);
    assertEquals(cache.selectAll(getDomainContext()).size(), 2);

    // as if the current transaction persisted a modified PDO
    cache.expire(getSession(), TABLE_NAME, 3);

    expectThrows(PdoCacheException.class, () -> cache.selectAll(getDomainContext()));
    assertEquals(cache.selectAllCount, 1, "the cache must not reload the table within the polluting transaction");
  }

  /**
   * Selecting a PDO modified within the same transaction via select must throw as well.
   */
  @Test
  public void testPollutionInSelectThrows() {
    store(1);
    store(2);
    cache.setStrategy(PdoCacheStrategy.LRU);
    assertEquals(cache.select(getDomainContext(), 1).getId(), 1L);

    cache.expire(getSession(), TABLE_NAME, 3);

    expectThrows(PdoCacheException.class, () -> cache.select(getDomainContext(), 2));
  }

  /**
   * LRU evicts when maxSize is exceeded.
   */
  @Test
  public void testLruEvicts() {
    for (long id = 1; id <= 10; id++) {
      store(id);
    }
    cache.setStrategy(PdoCacheStrategy.LRU);
    cache.setMaxSize(4);
    for (long id = 1; id <= 10; id++) {
      assertEquals(cache.select(getDomainContext(), id).getId(), id);
    }
    assertTrue(cache.getSize() <= 5, "cache size " + cache.getSize() + " exceeds maxSize + 1");
  }

  /**
   * FORGET evicts everything when maxSize is exceeded.
   */
  @Test
  public void testForgetEvicts() {
    for (long id = 1; id <= 10; id++) {
      store(id);
    }
    cache.setStrategy(PdoCacheStrategy.FORGET);
    cache.setMaxSize(4);
    for (long id = 1; id <= 10; id++) {
      cache.select(getDomainContext(), id);
    }
    assertTrue(cache.getSize() <= 5, "cache size " + cache.getSize() + " exceeds maxSize + 1");
  }

  /**
   * Shrinking must keep the serial infos of the surviving PDOs.<br>
   * Otherwise, modifications of the survivors would no longer expire them.
   */
  @Test
  public void testShrinkingKeepsSerialInfos() {
    for (long id = 1; id <= 5; id++) {
      store(id);
    }
    cache.setStrategy(PdoCacheStrategy.LRU);
    cache.setKeepQuota(25);     // keep less than half -> clear the indexes and add the survivors again
    cache.setMaxSize(4);
    for (long id = 1; id <= 5; id++) {
      cache.select(getDomainContext(), id);
    }
    assertEquals(cache.getSize(), 5);

    // shrinks to the most recently used PDO, which is then found in the cache
    assertEquals(cache.select(getDomainContext(), 5).getId(), 5L);
    assertEquals(cache.getSize(), 1);
    assertTrue(cache.isInitialized(), "serial infos lost while PDOs are still cached");
  }

  /**
   * A null key is not in the cache and does not crash the cache.
   */
  @Test
  public void testNullKey() {
    store(1);
    cache.setStrategy(PdoCacheStrategy.LRU);
    PdoCacheIndex<DemoCached, String> nameIndex = PdoCacheFactory.getInstance().createCacheIndex(
      "DemoCached:NAME", (context, name) -> null, pdo -> "name" + pdo.getId());
    assertNull(cache.select(nameIndex, getDomainContext(), null, true));
  }

  /**
   * A PDO excluded by a filtered index (key is null) can still be added to the cache.
   */
  @Test
  public void testAddWithFilteredIndex() {
    cache.setStrategy(PdoCacheStrategy.LRU);
    // only even IDs are indexed
    PdoCacheIndex<DemoCached, Long> evenIndex = PdoCacheFactory.getInstance().createCacheIndex(
      "DemoCached:EVEN", (context, id) -> null, pdo -> pdo.getId() % 2 == 0 ? pdo.getId() : null);
    cache.addIndex(evenIndex);

    DemoCached odd = create(1);
    assertTrue(cache.add(odd));
    assertSame(cache.select(getDomainContext(), 1, false), odd);

    DemoCached even = create(2);
    assertTrue(cache.add(even));
    assertSame(cache.select(evenIndex, getDomainContext(), 2L, false), even);
  }

  /**
   * Adding a PDO already in the cache does not remove the cached one.
   */
  @Test
  public void testAddDuplicateKeepsTheOriginal() {
    cache.setStrategy(PdoCacheStrategy.LRU);
    DemoCached original = create(1);
    assertTrue(cache.add(original));
    assertFalse(cache.add(create(1)));
    assertSame(cache.select(getDomainContext(), 1, false), original);
  }

  /**
   * The ID index cannot be removed.
   */
  @Test
  public void testIdIndexCannotBeRemoved() {
    PdoCacheIndex<DemoCached, ?> idIndex = cache.getIndexes().getFirst();
    expectThrows(PdoCacheException.class, () -> cache.removeIndex(idIndex));
    assertEquals(cache.getIndexes().size(), 1);
  }

  /**
   * Exceeding the hard limit is not a cache inconsistency: no invalidation, no retry.
   */
  @Test
  public void testHardLimitIsNotRetried() {
    store(1);
    store(2);
    cache.setMaxSize(1);
    cache.setMaxSizeHardLimit(true);
    PdoRuntimeException ex = expectThrows(PdoRuntimeException.class, () -> cache.selectAll(getDomainContext()));
    assertFalse(ex instanceof PdoCacheException);
    assertEquals(cache.selectAllCount, 1);
  }

  /**
   * The factory's list of caches is a snapshot.
   */
  @Test
  public void testGetAllCachesIsASnapshot() {
    DefaultPdoCacheFactory factory = new DefaultPdoCacheFactory();
    Collection<PdoCache<? extends PersistentDomainObject<?>>> caches = factory.getAllCaches();
    factory.createCache(DemoCached.class, false);
    assertTrue(caches.isEmpty());
    assertEquals(factory.getAllCaches().size(), 1);
  }

}
