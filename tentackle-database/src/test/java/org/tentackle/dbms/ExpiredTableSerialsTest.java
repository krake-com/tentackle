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
import org.tentackle.session.ModificationTracker;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * Tests that {@link AbstractDbObject#getExpiredTableSerials(long, long)} only answers from the
 * {@link TableSerialExpirationBacklog} if the backlog covers the whole requested range.
 * <p>
 * The object IDs are deliberately much larger than the tableserials, so that comparing an ID with
 * the requested max. serial cannot accidentally give the right answer.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ExpiredTableSerialsTest extends AbstractDbTest {

  /**
   * A db object reading its expired tableserials from memory instead of the database.
   */
  private static class ExpiringObject extends AbstractDbObject<ExpiringObject> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final DbObjectClassVariables<ExpiringObject> CLASSVARIABLES =
        DbObjectClassVariables.create(ExpiringObject.class, 0, "expiredtableserialstest");

    @SuppressWarnings("serial")
    private final List<IdSerialTuple> storage = new ArrayList<>();    // the "database"
    private int selectCount;                                          // number of database queries

    private ExpiringObject(Db db) {
      super(db);
    }

    @Override
    public DbObjectClassVariables<ExpiringObject> getClassVariables() {
      return CLASSVARIABLES;
    }

    @Override
    public boolean isTableSerialProvided() {
      return true;
    }

    @Override
    public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial, long maxSerial) {
      selectCount++;
      List<IdSerialTuple> tuples = new ArrayList<>();
      for (IdSerialTuple tuple : storage) {
        if (tuple.getSerial() > oldSerial && tuple.getSerial() <= maxSerial) {
          tuples.add(tuple);
        }
      }
      return tuples;
    }
  }


  @Test
  public void testPartiallyCoveredRangeQueriesDatabase() {
    assertFalse(ModificationTracker.getInstance().isLocalClientMode(), "the backlog is not used in local client mode");

    ExpiringObject obj = new ExpiringObject(getSession());
    obj.storage.add(new IdSerialTuple(1001, 1));
    obj.storage.add(new IdSerialTuple(1002, 2));

    // not in the backlog yet: read from the database
    assertEquals(serials(obj.getExpiredTableSerials(0, 2)), List.of(1L, 2L));
    assertEquals(obj.selectCount, 1);

    // fully covered by the backlog: no database query
    assertEquals(serials(obj.getExpiredTableSerials(1, 2)), List.of(2L));
    assertEquals(obj.selectCount, 1, "the backlog covers the range, but the database was queried");

    // another modification: the backlog covers the requested range only partially
    obj.storage.add(new IdSerialTuple(1003, 3));
    assertEquals(serials(obj.getExpiredTableSerials(1, 3)), List.of(2L, 3L),
                 "the partially covering backlog was taken for complete");
    assertEquals(obj.selectCount, 2);
  }


  private List<Long> serials(Collection<IdSerialTuple> tuples) {
    List<Long> serials = new ArrayList<>();
    for (IdSerialTuple tuple : tuples) {
      serials.add(tuple.getSerial());
    }
    return serials;
  }

}
