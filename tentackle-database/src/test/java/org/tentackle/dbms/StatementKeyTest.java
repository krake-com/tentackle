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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link StatementKey}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class StatementKeyTest {

  @Test
  public void testClassKeyWithVariant() {
    StatementId id = new StatementId();
    StatementKey key = new StatementKey(id, String.class, "myVariant");
    assertSame(key.getStatementId(), id);
    assertEquals(key.getUsingClass(), String.class);
    assertEquals(key.getVariant(), "myVariant");
    assertNull(key.getSql(), "sql must be null for a class key");
  }

  @Test
  public void testClassKeyWithoutVariant() {
    StatementId id = new StatementId();
    StatementKey key = new StatementKey(id, String.class);
    assertSame(key.getStatementId(), id);
    assertEquals(key.getUsingClass(), String.class);
    assertNull(key.getVariant(), "variant must be null when not given");
    assertNull(key.getSql());
  }

  @Test
  public void testSqlKey() {
    StatementKey key = new StatementKey("select 1");
    assertNull(key.getStatementId(), "statementId must be null for an sql key");
    assertNull(key.getUsingClass(), "usingClass must be null for an sql key");
    assertNull(key.getVariant());
    assertEquals(key.getSql(), "select 1");
  }

  @Test
  public void testNullArgumentsRejected() {
    assertThrows(NullPointerException.class, () -> new StatementKey(null, String.class, "v"));
    assertThrows(NullPointerException.class, () -> new StatementKey(new StatementId(), null, "v"));
    assertThrows(NullPointerException.class, () -> new StatementKey((String) null));
  }

  @Test
  public void testEmptySqlRejected() {
    assertThrows(IllegalArgumentException.class, () -> new StatementKey(""));
  }

  @Test
  public void testEqualsAndHashCodeForClassKeys() {
    StatementId id = new StatementId();
    StatementKey a = new StatementKey(id, String.class, "v");
    StatementKey b = new StatementKey(id, String.class, "v");
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    // reflexivity
    assertEquals(a, a);
  }

  @Test
  public void testInequalityOnEachDistinguishingField() {
    StatementId id1 = new StatementId();
    StatementId id2 = new StatementId();
    StatementKey base = new StatementKey(id1, String.class, "v");

    assertNotEquals(base, new StatementKey(id2, String.class, "v"));       // different id
    assertNotEquals(base, new StatementKey(id1, Integer.class, "v"));      // different class
    assertNotEquals(base, new StatementKey(id1, String.class, "other"));   // different variant
    assertNotEquals(base, new StatementKey(id1, String.class));            // null vs. set variant
    assertNotEquals(base, new StatementKey("some sql"));                   // class key vs. sql key
  }

  @Test
  public void testEqualsForSqlKeys() {
    assertEquals(new StatementKey("select 1"), new StatementKey("select 1"));
    assertEquals(new StatementKey("select 1").hashCode(), new StatementKey("select 1").hashCode());
    assertNotEquals(new StatementKey("select 1"), new StatementKey("select 2"));
  }

  @Test
  public void testEqualsWithNullAndForeignType() {
    StatementKey key = new StatementKey("select 1");
    assertFalse(key.equals(null));
    assertFalse(key.equals("select 1"));
  }

  @Test
  public void testToStringClassKey() {
    StatementId id = new StatementId();
    String s = new StatementKey(id, String.class).toString();
    // <SimpleName#id>
    assertEquals(s, "<String#" + id + ">");

    String withVariant = new StatementKey(id, String.class, "v").toString();
    assertEquals(withVariant, "<String/v#" + id + ">");
  }

  @Test
  public void testToStringSqlKey() {
    assertEquals(new StatementKey("select 1").toString(), "<select 1>");
  }

  @Test
  public void testUsableAsMapKey() {
    StatementId id = new StatementId();
    java.util.Map<StatementKey, String> map = new java.util.HashMap<>();
    map.put(new StatementKey(id, String.class, "v"), "value");
    assertTrue(map.containsKey(new StatementKey(id, String.class, "v")));
    assertEquals(map.get(new StatementKey(id, String.class, "v")), "value");
  }

}
