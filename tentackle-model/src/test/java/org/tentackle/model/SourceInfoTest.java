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

package org.tentackle.model;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * Tests the {@link SourceInfo} value object.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class SourceInfoTest {

  @Test
  public void testConstructorDefaults() {
    SourceInfo onlyName = new SourceInfo("Foo.java");
    assertEquals(onlyName.getFileName(), "Foo.java");
    assertEquals(onlyName.getLineNumber(), 0);
    assertEquals(onlyName.getPosition(), 0);

    SourceInfo withLine = new SourceInfo("Foo.java", 5);
    assertEquals(withLine.getLineNumber(), 5);
    assertEquals(withLine.getPosition(), 0);

    SourceInfo full = new SourceInfo("Foo.java", 5, 3);
    assertEquals(full.getLineNumber(), 5);
    assertEquals(full.getPosition(), 3);
  }

  @Test
  public void testGetShortFileName() {
    assertEquals(new SourceInfo("Foo.java").getShortFileName(), "Foo.java");
    assertEquals(new SourceInfo("a/b/c/Foo.java").getShortFileName(), "Foo.java");
    assertEquals(new SourceInfo("/absolute/path/Foo.java").getShortFileName(), "Foo.java");
  }

  @Test
  public void testToString() {
    assertEquals(new SourceInfo("a/b/Foo.java").toString(), "Foo.java");
    assertEquals(new SourceInfo("a/b/Foo.java", 12).toString(), "Foo.java[12]");
    assertEquals(new SourceInfo("a/b/Foo.java", 12, 4).toString(), "Foo.java[12,4]");
    // position is only printed together with a line number
    assertEquals(new SourceInfo("Foo.java", 0, 4).toString(), "Foo.java");
  }

  @Test
  public void testAdd() {
    SourceInfo base = new SourceInfo("Foo.java", 10, 2);
    SourceInfo shifted = base.add(5, 3);
    assertEquals(shifted.getFileName(), "Foo.java");
    assertEquals(shifted.getLineNumber(), 15);
    assertEquals(shifted.getPosition(), 5);
    // original is unchanged
    assertEquals(base.getLineNumber(), 10);
    assertEquals(base.getPosition(), 2);
  }

  @Test
  public void testEqualsAndHashCode() {
    SourceInfo a = new SourceInfo("Foo.java", 5, 3);
    SourceInfo b = new SourceInfo("Foo.java", 5, 3);
    SourceInfo differentLine = new SourceInfo("Foo.java", 6, 3);
    SourceInfo differentPos = new SourceInfo("Foo.java", 5, 4);
    SourceInfo differentName = new SourceInfo("Bar.java", 5, 3);

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(a, a);

    assertNotEquals(a, differentLine);
    assertNotEquals(a, differentPos);
    assertNotEquals(a, differentName);
    assertNotEquals(a, null);
    assertNotEquals(a, "Foo.java");
  }
}
