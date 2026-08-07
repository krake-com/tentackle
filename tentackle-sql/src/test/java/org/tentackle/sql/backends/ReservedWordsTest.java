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

package org.tentackle.sql.backends;

import org.testng.annotations.Test;

import org.tentackle.sql.Backend;

import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Testing the lazily built sets of reserved words.<br>
 * The cache must belong to the backend instance, not to the backend class:
 * a static cache would be shared by all backends inheriting the same
 * {@code getReservedWords()} and would be written under the monitor of whichever
 * instance happened to build it first.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ReservedWordsTest {

  @Test
  public void testCacheIsNotSharedBetweenBackends() {
    // Db2 and MsSql both inherit AbstractSql2003Backend.getReservedWords()
    Set<String> db2Words = new Db2().getReservedWords();
    Set<String> msSqlWords = new MsSql().getReservedWords();
    assertDistinct(db2Words, msSqlWords, "Db2 and MsSql must not share the cached set of reserved words");
    assertEquals(db2Words, msSqlWords, "both backends are plain SQL2003, hence the same words");
  }

  @Test
  public void testCacheIsNotSharedBetweenInstances() {
    assertDistinct(new H2().getReservedWords(), new H2().getReservedWords(),
                   "H2 instances must not share the cached set of reserved words");
    assertDistinct(new Postgres().getReservedWords(), new Postgres().getReservedWords(),
                   "Postgres instances must not share the cached set of reserved words");
    assertDistinct(new MySql().getReservedWords(), new MySql().getReservedWords(),
                   "MySql instances must not share the cached set of reserved words");
  }

  @Test
  public void testCacheIsBuiltOnlyOnce() {
    Backend backend = new Postgres();
    assertSameInstanceOnSecondCall(backend);
    backend = new Db2();
    assertSameInstanceOnSecondCall(backend);
  }

  @Test
  public void testBackendSpecificWords() {
    // every backend inherits the SQL92 words
    assertTrue(new H2().getReservedWords().contains("SELECT"));
    assertTrue(new Postgres().getReservedWords().contains("SELECT"));
    assertTrue(new Db2().getReservedWords().contains("SELECT"));

    // ... plus the SQL2003 words, if applicable
    assertTrue(new Db2().getReservedWords().contains("WINDOW"));
    assertFalse(new H2().getReservedWords().contains("WINDOW"), "H2 is an SQL92 backend");

    // ... plus its own
    assertTrue(new H2().getReservedWords().contains("UUID"));
    assertTrue(new Postgres().getReservedWords().contains("TEXT"));
    assertFalse(new H2().getReservedWords().contains("TEXT"), "TEXT is reserved in postgres only");
    assertFalse(new Db2().getReservedWords().contains("UUID"), "UUID is not reserved in DB2");
  }

  /**
   * Asserts that the set of reserved words is built only once per instance.
   *
   * @param backend the backend
   */
  private void assertSameInstanceOnSecondCall(Backend backend) {
    Set<String> words = backend.getReservedWords();
    assertSame(words, backend.getReservedWords(), "the set of reserved words must be cached");
  }

  /**
   * Asserts that two sets of reserved words are not the very same instance.<br>
   * Unlike {@code assertNotSame} this does not dump both sets into the failure message.
   *
   * @param first the first set
   * @param second the second set
   * @param message the message in case of a failure
   */
  private void assertDistinct(Set<String> first, Set<String> second, String message) {
    assertTrue(first != second, message);   // comparing the references is the point here
  }

}
