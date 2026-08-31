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

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link ObjectSequenceId} against a dedicated H2 sequence created just for this test class
 * (not the shared {@code object_sequence_id} sequence configured in {@code pom.xml}, which other
 * tests in the suite may already be consuming — that would make the multiplier-boundary
 * assertions below non-deterministic).
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ObjectSequenceIdTest extends AbstractDbTest {

  private static final String SEQUENCE_NAME = "objseqidtest_seq";
  private static final int SEQUENCE_MULTIPLIER = 10;
  private static final int GLOBAL_MULTIPLIER = 1;

  public ObjectSequenceIdTest() {
    super(TransactionType.METHOD);
  }

  /**
   * Only the tests that actually call {@link ObjectSequenceId#nextId} touch the database, so only
   * their sequences need to exist. They are created once here, rather than per test method, so
   * the per-method transaction (which rolls back after every test) never wraps the DDL.
   */
  @BeforeClass(dependsOnMethods = "beforeClass")
  public void createSequences() {
    Db db = getSession();
    Backend backend = db.getBackend();
    for (String suffix : new String[] { "_mono", "_boundary", "_global" }) {
      // each StatementWrapper is single-use: it detaches the session once executeUpdate() returns
      try (StatementWrapper st = db.createStatement()) {
        st.executeUpdate(backend.sqlCreateSequence(SEQUENCE_NAME + suffix, null, null));
      }
    }
  }


  @Test
  public void testMonotonicUniquenessWithinMultiplier() {
    ObjectSequenceId source = ObjectSequenceId.get(SEQUENCE_NAME + "_mono", SEQUENCE_MULTIPLIER, GLOBAL_MULTIPLIER);
    long previous = source.nextId(getSession());
    for (int i = 1; i < SEQUENCE_MULTIPLIER; i++) {
      long next = source.nextId(getSession());
      assertTrue(next > previous, "ids must be strictly increasing");
      previous = next;
    }
  }


  @Test
  public void testSequenceCrossesMultiplierBoundary() {
    ObjectSequenceId source = ObjectSequenceId.get(SEQUENCE_NAME + "_boundary", SEQUENCE_MULTIPLIER, GLOBAL_MULTIPLIER);
    long lastOfFirstBlock = 0;
    for (int i = 0; i < SEQUENCE_MULTIPLIER; i++) {
      lastOfFirstBlock = source.nextId(getSession());
    }
    long firstOfSecondBlock = source.nextId(getSession());
    assertTrue(firstOfSecondBlock > lastOfFirstBlock, "the id after the boundary must still increase");
    assertTrue(firstOfSecondBlock - lastOfFirstBlock >= 1,
               "a fresh sequence fetch must not simply continue the exhausted local counter");
  }


  @Test
  public void testGlobalMultiplierIsApplied() {
    int globalMultiplier = 2;
    ObjectSequenceId source = ObjectSequenceId.get(SEQUENCE_NAME + "_global", SEQUENCE_MULTIPLIER, globalMultiplier);
    for (int i = 0; i < SEQUENCE_MULTIPLIER; i++) {
      long id = source.nextId(getSession());
      assertEquals(id % globalMultiplier, 0, "id " + id + " must be a multiple of the global multiplier");
    }
  }


  @Test
  public void testGetIsSingletonPerName() {
    ObjectSequenceId first = ObjectSequenceId.get(SEQUENCE_NAME + "_singleton");
    ObjectSequenceId second = ObjectSequenceId.get(SEQUENCE_NAME + "_singleton");
    assertSame(first, second, "id sources are unique by name");
  }


  @Test
  public void testConflictingMultipliersRejected() {
    String name = SEQUENCE_NAME + "_conflict";
    ObjectSequenceId.get(name, 10, 1);
    assertThrows(PersistenceException.class, () -> ObjectSequenceId.get(name, 20, 1));
  }


  @Test
  public void testIsLockFree() {
    ObjectSequenceId source = ObjectSequenceId.get(SEQUENCE_NAME + "_lockfree");
    assertTrue(source.isLockFree(), "database sequences are not part of the transaction");
  }

}
