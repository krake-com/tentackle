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

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link PdoMember}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PdoMemberTest {

  private static Method getter() throws NoSuchMethodException {
    return String.class.getMethod("length");
  }

  private static Method setter() throws NoSuchMethodException {
    return StringBuilder.class.getMethod("setLength", int.class);
  }

  @Test
  public void testTypeText() {
    assertEquals(PdoMember.Type.ATTRIBUTE.getText(), "attribute");
    assertEquals(PdoMember.Type.RELATION.getText(), "relation");
  }

  @Test
  public void testGetters() throws NoSuchMethodException {
    Method g = getter();
    Method s = setter();
    PdoMember member = new PdoMember(PdoMember.Type.ATTRIBUTE, "name", g, s,
                                     true, false, false, true, "a comment", 7);
    assertEquals(member.getType(), PdoMember.Type.ATTRIBUTE);
    assertEquals(member.getName(), "name");
    assertSame(member.getGetter(), g);
    assertSame(member.getSetter(), s);
    assertTrue(member.isComponent());
    assertFalse(member.isParent());
    assertFalse(member.isEmbedded());
    assertTrue(member.isDomainKey());
    assertEquals(member.getComment(), "a comment");
    assertEquals(member.getOrdinal(), 7);
  }

  @Test
  public void testEqualsAndHashCodeByNameOnly() {
    PdoMember a = new PdoMember(PdoMember.Type.ATTRIBUTE, "shared", null, null,
                                false, false, false, false, "first", 1);
    // identical name but every other field differs -> still equal
    PdoMember b = new PdoMember(PdoMember.Type.RELATION, "shared", null, null,
                                true, true, false, true, "second", 99);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    PdoMember c = new PdoMember(PdoMember.Type.ATTRIBUTE, "other", null, null,
                                false, false, false, false, null, 1);
    assertNotEquals(a, c);
    assertNotEquals(a, null);
    assertNotEquals(a, "shared");
  }

  @Test
  public void testToStringAttribute() {
    PdoMember member = new PdoMember(PdoMember.Type.ATTRIBUTE, "id", null, null,
                                     false, false, false,false, null, 0);
    assertEquals(member.toString(), "attribute id");
  }

  @Test
  public void testToStringAttributeWithComment() {
    PdoMember member = new PdoMember(PdoMember.Type.ATTRIBUTE, "id", null, null,
                                     false, false, false, false, "the id", 0);
    assertEquals(member.toString(), "attribute id \"the id\"");
  }

  /**
   * {@code @Persistent.comment()} defaults to the empty string, which is no comment.
   */
  @Test
  public void testToStringAttributeWithEmptyComment() {
    PdoMember member = new PdoMember(PdoMember.Type.ATTRIBUTE, "id", null, null,
                                     false, false, false, false, "", 0);
    assertEquals(member.toString(), "attribute id");
  }

  @Test
  public void testToStringComponentParentRelation() {
    PdoMember member = new PdoMember(PdoMember.Type.RELATION, "children", null, null,
                                     true, true, false, false, null, 0);
    // component and parent prefixes only apply to relations
    assertEquals(member.toString(), "component parent relation children");
  }

  @Test
  public void testComponentAndParentPrefixesIgnoredForAttributes() {
    // even if the flags are set, an attribute must not print the relation-only prefixes
    PdoMember member = new PdoMember(PdoMember.Type.ATTRIBUTE, "x", null, null,
                                     true, true, false,false, null, 0);
    assertEquals(member.toString(), "attribute x");
  }

  @Test
  public void testNullMethodsAllowed() {
    PdoMember member = new PdoMember(PdoMember.Type.ATTRIBUTE, "x", null, null,
                                     false, false, false, false, null, 0);
    assertNull(member.getGetter());
    assertNull(member.getSetter());
    assertNull(member.getComment());
  }

}
