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

package org.tentackle.dbms;

import org.testng.annotations.Test;

import org.tentackle.misc.IdSerialTuple;

import java.util.List;
import java.util.TreeSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link TableSerialExpirationBacklog}.
 * <p>
 * All tests use {@code mergeHistory == false} so that the backlog can be exercised
 * in isolation, without touching the {@link TableSerialHistory} singleton (which needs a session).
 */
@SuppressWarnings("missing-explicit-ctor")
public class TableSerialExpirationBacklogTest {

  private static IdSerialTuple tuple(long id, long serial) {
    return new IdSerialTuple(id, serial);
  }

  private static TableSerialExpirationBacklog newBacklog() {
    // one expiration set covering table-serials (2, 5]: id 10 at serial 3, id 11 at serial 5
    TableSerialExpirationBacklog backlog = new TableSerialExpirationBacklog("test", 4);
    backlog.addExpiration(2, 5, List.of(tuple(10, 3), tuple(11, 5)), false);
    return backlog;
  }

  @Test
  public void testRetrieveFullRange() {
    TreeSet<IdSerialTuple> result = newBacklog().getExpiration(2, 5);
    assertNotNull(result);
    assertEquals(result.size(), 2);
    // ordered by abs(tableserial): 3 before 5
    assertEquals(result.first().getId(), 10);
    assertEquals(result.last().getId(), 11);
  }

  @Test
  public void testRetrievePartialRangeExcludesOutOfBoundSerials() {
    // serial 5 is above the upper bound 4 -> only id 10 (serial 3) remains
    TreeSet<IdSerialTuple> result = newBacklog().getExpiration(2, 4);
    assertNotNull(result);
    assertEquals(result.size(), 1);
    assertEquals(result.first().getId(), 10);
  }

  @Test
  public void testMatchingRangeWithNoTuplesReturnsEmptySet() {
    // (5, 5] matches the set's serial range but contains no serial in the open/closed bounds
    TreeSet<IdSerialTuple> result = newBacklog().getExpiration(5, 5);
    assertNotNull(result, "a matching set must yield a (possibly empty) result, not null");
    assertTrue(result.isEmpty());
  }

  @Test
  public void testOutdatedQueryReturnsNull() {
    // minSerial (1) below the backlog's minSerial (2) -> caller must ask the database
    assertNull(newBacklog().getExpiration(1, 5));
  }

  @Test
  public void testIllegalRangeIsIgnored() {
    TableSerialExpirationBacklog backlog = newBacklog();
    backlog.addExpiration(7, 7, List.of(tuple(99, 7)), false);   // minSerial >= maxSerial
    backlog.addExpiration(8, 6, List.of(tuple(98, 6)), false);   // minSerial > maxSerial
    // unchanged
    assertEquals(backlog.toString(), "test[2-5]");
    assertEquals(backlog.getExpiration(2, 5).size(), 2);
  }

  @Test
  public void testDecreasingMaxSerialIsIgnored() {
    TableSerialExpirationBacklog backlog = newBacklog();
    // maxSerial 4 is below the current maxSerial 5 -> must be refused
    backlog.addExpiration(2, 4, List.of(tuple(50, 4)), false);
    assertEquals(backlog.toString(), "test[2-5]");
    assertNull(backlog.getExpiration(2, 5).stream().filter(t -> t.getId() == 50).findAny().orElse(null));
  }

  @Test
  public void testNullTuplesIsNoOp() {
    TableSerialExpirationBacklog backlog = newBacklog();
    backlog.addExpiration(5, 9, null, false);
    assertEquals(backlog.toString(), "test[2-5]");
  }

  @Test
  public void testEmptyTuplesAreNotAdded() {
    TableSerialExpirationBacklog backlog = newBacklog();
    backlog.addExpiration(5, 9, List.of(), false);
    // an empty expiration set does not advance the serial bounds
    assertEquals(backlog.toString(), "test[2-5]");
  }

  @Test
  public void testSecondExpirationSetExtendsRange() {
    TableSerialExpirationBacklog backlog = newBacklog();
    backlog.addExpiration(5, 8, List.of(tuple(20, 7)), false);
    assertEquals(backlog.toString(), "test[2-8]");

    TreeSet<IdSerialTuple> result = backlog.getExpiration(2, 8);
    assertNotNull(result);
    assertEquals(result.size(), 3);   // ids 10, 11, 20 across both sets
    assertEquals(result.last().getId(), 20, "serial 7 (id 20) is the highest");
  }

  @Test
  public void testEnlargeKeepsChronologicalOrder() {
    TableSerialExpirationBacklog backlog = new TableSerialExpirationBacklog("test", 4);
    // 6 sets (s-1, s] with a single tuple at serial s: the ring wraps, the oldest kept set is (2, 3]
    for (long s = 1; s <= 6; s++) {
      backlog.addExpiration(s - 1, s, List.of(tuple(s, s)), false);
    }
    assertEquals(backlog.toString(), "test[2-6]");

    // outdated query enlarges the ring while it is wrapped
    assertNull(backlog.getExpiration(0, 6));

    // all sets still in the backlog must be found, not only those up to the wrap position
    TreeSet<IdSerialTuple> result = backlog.getExpiration(2, 6);
    assertNotNull(result);
    assertEquals(result.stream().map(IdSerialTuple::getId).toList(), List.of(3L, 4L, 5L, 6L));

    // adding after the enlargement continues in chronological order
    backlog.addExpiration(6, 7, List.of(tuple(7, 7)), false);
    result = backlog.getExpiration(2, 7);
    assertNotNull(result);
    assertEquals(result.stream().map(IdSerialTuple::getId).toList(), List.of(3L, 4L, 5L, 6L, 7L));
  }

  @Test
  public void testToString() {
    assertEquals(newBacklog().toString(), "test[2-5]");
    assertEquals(new TableSerialExpirationBacklog("empty").toString(), "empty[0-0]");
  }

}
