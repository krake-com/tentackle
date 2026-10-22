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

package org.tentackle.sql;

import org.tentackle.sql.backends.H2;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests the SQL rendering helpers in {@link NonStandardCommons} that do not require a backend.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class NonStandardCommonsTest {

  @Test
  public void testSequenceWithStartAndIncrement() {
    assertEquals(NonStandardCommons.sqlCreateSequence("seq", 100L, 5L),
                 "CREATE SEQUENCE seq INCREMENT BY 5 START WITH 100");
  }

  @Test
  public void testSequenceWithoutStart() {
    assertEquals(NonStandardCommons.sqlCreateSequence("seq", null, 5L),
                 "CREATE SEQUENCE seq INCREMENT BY 5");
  }

  @Test
  public void testSequenceWithoutIncrement() {
    assertEquals(NonStandardCommons.sqlCreateSequence("seq", 100L, null),
                 "CREATE SEQUENCE seq START WITH 100");
  }

  @Test
  public void testSequenceNameOnly() {
    assertEquals(NonStandardCommons.sqlCreateSequence("seq", null, null),
                 "CREATE SEQUENCE seq");
  }


  // ---- the COMMENT ON statements -------------------------------------------

  private final Backend backend = new H2();

  @Test
  public void testCommentOnTable() {
    assertEquals(NonStandardCommons.sqlCreateCommentOnTable(backend, "foo", "the foo table"),
                 "COMMENT ON TABLE foo IS 'the foo table';\n");
    // dropping a table comment means commenting it with the empty string
    assertEquals(NonStandardCommons.sqlCreateCommentOnTable(backend, "s.foo", null),
                 "COMMENT ON TABLE s.foo IS '';\n");
    // a quote in the comment must not end the literal
    assertEquals(NonStandardCommons.sqlCreateCommentOnTable(backend, "foo", "it's foo"),
                 "COMMENT ON TABLE foo IS 'it''s foo';\n");
  }

  @Test
  public void testCommentOnColumn() {
    assertEquals(NonStandardCommons.sqlCreateCommentOnColumn(backend, "foo", "name", "the name"),
                 "COMMENT ON COLUMN foo.name IS 'the name';\n");
    // a column comment is dropped with NULL, not with an empty string
    assertEquals(NonStandardCommons.sqlCreateCommentOnColumn(backend, "foo", "name", null),
                 "COMMENT ON COLUMN foo.name IS NULL;\n");
  }

  /**
   * An index lives in the schema of its table, but is not qualified by the table name.
   */
  @Test
  public void testCommentOnIndex() {
    assertEquals(NonStandardCommons.sqlCreateCommentOnIndex(backend, "foo", "foo_name", "by name"),
                 "COMMENT ON INDEX foo_name IS 'by name';\n");
    assertEquals(NonStandardCommons.sqlCreateCommentOnIndex(backend, "s.foo", "foo_name", "by name"),
                 "COMMENT ON INDEX s.foo_name IS 'by name';\n", "the schema of the table is prepended");
    assertEquals(NonStandardCommons.sqlCreateCommentOnIndex(backend, "foo", "foo_name", null),
                 "COMMENT ON INDEX foo_name IS NULL;\n");
  }

  @Test
  public void testCommentOnSequence() {
    assertEquals(NonStandardCommons.sqlCreateCommentOnSequence(backend, "seq", "the sequence"),
                 "COMMENT ON SEQUENCE seq IS 'the sequence';\n");
    assertEquals(NonStandardCommons.sqlCreateCommentOnSequence(backend, "s.seq", null),
                 "COMMENT ON SEQUENCE s.seq IS NULL;\n");
  }
}
