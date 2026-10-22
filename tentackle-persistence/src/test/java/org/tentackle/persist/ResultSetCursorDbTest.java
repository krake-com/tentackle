/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.persist;

import org.testng.annotations.Test;

import org.tentackle.misc.ScrollableResource;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Database-backed tests for {@link ResultSetCursor}.
 * <p>
 * The cursor is a scrolling state machine over a JDBC result set, and none of that state can be
 * observed without a real query: {@link ResultSetCursorTest} can only assert the thread-safety of
 * its cleanup fields. Everything else is verified here.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ResultSetCursorDbTest extends AbstractPersistenceDbTest {

  /**
   * Creates and saves the given number of entities, named {@code e1} ... {@code eN}.
   *
   * @param count the number of entities
   */
  private void createEntities(int count) {
    for (int i = 1; i <= count; i++) {
      DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
      DemoEntityPersistenceImpl po = (DemoEntityPersistenceImpl) pdo.getPersistenceDelegate();
      po.setName("e" + i);
      po.save();
    }
  }

  /**
   * Opens a cursor over all entities, ordered by ID, i.e. by creation order.
   *
   * @return the cursor
   */
  private ScrollableResource<DemoEntity> cursor() {
    return ((DemoEntityPersistenceImpl) Pdo.create(DemoEntity.class, getDomainContext())
                                           .getPersistenceDelegate()).selectAllAsCursor();
  }

  private String name(DemoEntity pdo) {
    return ((DemoEntityPersistenceImpl) pdo.getPersistenceDelegate()).getName();
  }


  @Test
  public void testFreshCursorIsBeforeTheFirstRow() {
    createEntities(3);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertEquals(cursor.getRow(), 0);
      assertTrue(cursor.isBeforeFirst());
      assertFalse(cursor.isAfterLast());
      assertFalse(cursor.isClosed());
    }
  }

  @Test
  public void testNextWalksThroughAllRows() {
    createEntities(3);
    List<String> names = new ArrayList<>();
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      while (cursor.next()) {
        names.add(name(cursor.get()));
      }
      assertFalse(cursor.next(), "past the last row");
      assertTrue(cursor.isAfterLast());
    }
    assertEquals(names, List.of("e1", "e2", "e3"));
  }

  @Test
  public void testFirstOnAnUntouchedCursorDelegatesToNext() {
    createEntities(3);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertTrue(cursor.first());
      assertEquals(cursor.getRow(), 1);
      assertEquals(name(cursor.get()), "e1");
    }
  }

  @Test
  public void testFirstOnTheFirstRowIsANoOp() {
    createEntities(3);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertTrue(cursor.next());
      assertEquals(cursor.getRow(), 1);
      assertTrue(cursor.first());
      assertEquals(cursor.getRow(), 1);
      assertEquals(name(cursor.get()), "e1");
    }
  }

  @Test
  public void testFirstRewindsFromAnyOtherRow() {
    createEntities(3);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertTrue(cursor.last());
      assertEquals(cursor.getRow(), 3);
      assertTrue(cursor.first());
      assertEquals(cursor.getRow(), 1);
      assertEquals(name(cursor.get()), "e1");
    }
  }

  @Test
  public void testFirstOnAnEmptyCursor() {
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertFalse(cursor.first());
      assertFalse(cursor.last());
      assertTrue(cursor.toList().isEmpty());
    }
  }

  @Test
  public void testLastAndPrevious() {
    createEntities(3);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertTrue(cursor.last());
      assertEquals(name(cursor.get()), "e3");
      assertTrue(cursor.previous());
      assertEquals(name(cursor.get()), "e2");
      assertTrue(cursor.previous());
      assertEquals(name(cursor.get()), "e1");
      assertFalse(cursor.previous(), "before the first row");
      assertTrue(cursor.isBeforeFirst());
    }
  }

  @Test
  public void testSetRow() {
    createEntities(4);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertTrue(cursor.setRow(3));
      assertEquals(cursor.getRow(), 3);
      assertEquals(name(cursor.get()), "e3");

      assertFalse(cursor.setRow(9), "no such row");
      assertTrue(cursor.isAfterLast());
    }
  }

  @Test
  public void testSetRowRejectsANegativeRow() {
    createEntities(1);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertTrue(expectThrows(PersistenceException.class, () -> cursor.setRow(-1))
                     .getMessage().contains("invalid row -1"));
    }
  }

  @Test
  public void testScroll() {
    createEntities(5);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertTrue(cursor.next());
      assertTrue(cursor.scroll(2));
      assertEquals(name(cursor.get()), "e3");
      assertTrue(cursor.scroll(-1));
      assertEquals(name(cursor.get()), "e2");
    }
  }

  @Test
  public void testBeforeFirstRewindsTheCursor() {
    createEntities(3);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertTrue(cursor.last());
      cursor.beforeFirst();
      assertTrue(cursor.isBeforeFirst());
      assertEquals(cursor.getRow(), 0);
      assertTrue(cursor.next());
      assertEquals(name(cursor.get()), "e1");
    }
  }

  @Test
  public void testAfterLastPositionsBehindTheLastRow() {
    createEntities(3);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      cursor.afterLast();
      assertTrue(cursor.isAfterLast());
      assertFalse(cursor.next(), "nothing beyond the last row");
    }
  }

  @Test
  public void testPreviousAfterAfterLastLandsOnTheLastRow() {
    createEntities(3);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      cursor.afterLast();
      assertTrue(cursor.previous());
      assertEquals(cursor.getRow(), 3, "the row is taken from the result set, not from a decrement");
      assertEquals(name(cursor.get()), "e3");
      assertTrue(cursor.previous());
      assertEquals(name(cursor.get()), "e2");
    }
  }

  @Test
  public void testAFailedPreviousLeavesTheCursorBeforeTheFirstRow() {
    createEntities(2);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertTrue(cursor.first());
      assertFalse(cursor.previous(), "already on the first row");
      assertTrue(cursor.isBeforeFirst());
      assertEquals(cursor.getRow(), 0);
      // and the counter is in sync again, so next() does not skip a row
      assertTrue(cursor.next());
      assertEquals(cursor.getRow(), 1);
      assertEquals(name(cursor.get()), "e1");
    }
  }

  @Test
  public void testToList() {
    createEntities(3);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      assertEquals(cursor.toList().stream().map(this::name).toList(), List.of("e1", "e2", "e3"));
      assertFalse(cursor.isClosed(), "toList() leaves the cursor open");
    }
  }

  @Test
  public void testToListAndClose() {
    createEntities(2);
    ScrollableResource<DemoEntity> cursor = cursor();
    assertEquals(cursor.toListAndClose().size(), 2);
    assertTrue(cursor.isClosed());
  }

  @Test
  public void testClosingTwiceIsAllowed() {
    createEntities(1);
    ScrollableResource<DemoEntity> cursor = cursor();
    cursor.close();
    assertTrue(cursor.isClosed());
    cursor.close();
    assertTrue(cursor.isClosed());
  }

  /*
   * About fetch():
   *
   * The block-wise variant of fetch() is driven by getFetchSize(), which ResultSetCursor reads
   * back from the JDBC result set. The fetch size is a hint the driver may discard, and H2 does
   * exactly that: getFetchSize() stays 0 no matter what setFetchSize() was given (pinned by
   * testH2DiscardsTheFetchSizeHint below). Every fetch() against H2 therefore takes the
   * "fetchMax < 1" fallback, which drains the cursor in one go and closes it.
   *
   * The block arithmetic itself is consequently not reachable from this module: it needs either a
   * driver honoring the hint (PostgreSQL) or a remote cursor, which keeps its own fetch size.
   */

  @Test
  public void testH2DiscardsTheFetchSizeHint() {
    createEntities(1);
    try (ScrollableResource<DemoEntity> cursor = cursor()) {
      cursor.setFetchSize(2);
      assertEquals(cursor.getFetchSize(), 0, "H2 ignores the fetch size, hence the fallback below");
    }
  }

  @Test
  public void testFetchDrainsAndClosesTheCursor() {
    createEntities(3);
    ScrollableResource<DemoEntity> cursor = cursor();
    List<DemoEntity> all = cursor.fetch();
    assertNotNull(all);
    assertEquals(all.stream().map(this::name).toList(), List.of("e1", "e2", "e3"));
    assertTrue(cursor.isClosed(), "the fallback closes the cursor");
    assertNull(cursor.fetch(), "a closed cursor fetches nothing");
  }

  @Test
  public void testFetchOfAnEmptyCursorYieldsAnEmptyList() {
    ScrollableResource<DemoEntity> cursor = cursor();
    List<DemoEntity> fetched = cursor.fetch();
    assertNotNull(fetched, "the fallback returns the (empty) list, not null");
    assertTrue(fetched.isEmpty());
    assertTrue(cursor.isClosed());
  }

  @Test
  public void testFetchOfASingleRow() {
    createEntities(1);
    ScrollableResource<DemoEntity> cursor = cursor();
    assertEquals(cursor.fetch().stream().map(this::name).toList(), List.of("e1"));
    assertTrue(cursor.isClosed());
  }

}
