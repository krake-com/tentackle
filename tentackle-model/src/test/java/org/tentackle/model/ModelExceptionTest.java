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

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link ModelException} and {@link ModelError}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ModelExceptionTest {

  /**
   * A minimal {@link ModelElement} for constructing errors/exceptions.
   */
  private static final class TestElement implements ModelElement {
    private final String name;
    private final SourceInfo sourceInfo;
    private final ModelElement parent;

    TestElement(String name, SourceInfo sourceInfo, ModelElement parent) {
      this.name = name;
      this.sourceInfo = sourceInfo;
      this.parent = parent;
    }

    @Override public String getName()          { return name; }
    @Override public int getOrdinal()          { return 0; }
    @Override public SourceInfo getSourceInfo() { return sourceInfo; }
    @Override public ModelElement getParent()  { return parent; }
    @Override public Model getModel()          { return null; }
  }


  // ---- ModelException ------------------------------------------------------

  @Test
  public void testMessageConstructor() {
    ModelException ex = new ModelException("boom");
    assertEquals(ex.getMessage(), "boom");
    assertNull(ex.getElement());
  }

  @Test
  public void testMessageAndCauseConstructor() {
    Throwable cause = new IllegalStateException("root");
    ModelException ex = new ModelException("boom", cause);
    assertEquals(ex.getMessage(), "boom");
    assertSame(ex.getCause(), cause);
    assertNull(ex.getElement());
  }

  @Test
  public void testMessageAndElementAppendsSourceInfo() {
    TestElement element = new TestElement("Foo", new SourceInfo("Foo.java", 7), null);
    ModelException ex = new ModelException("boom", element);
    assertSame(ex.getElement(), element);
    assertEquals(ex.getMessage(), "boom" + ModelException.AT_ELEMENT + "Foo.java[7]");
  }

  @Test
  public void testMessageNotAppendedTwice() {
    TestElement element = new TestElement("Foo", new SourceInfo("Foo.java", 7), null);
    // message already mentions the source info -> not appended again
    ModelException ex = new ModelException("problem at Foo.java[7]", element);
    assertEquals(ex.getMessage(), "problem at Foo.java[7]");
  }

  @Test
  public void testElementWithoutSourceInfoLeavesMessage() {
    TestElement element = new TestElement("Foo", null, null);
    ModelException ex = new ModelException("boom", element);
    assertEquals(ex.getMessage(), "boom");
  }

  @Test
  public void testIsRelatedToWalksParentChain() {
    TestElement root = new TestElement("root", null, null);
    TestElement mid = new TestElement("mid", null, root);
    TestElement leaf = new TestElement("leaf", null, mid);
    TestElement unrelated = new TestElement("other", null, null);

    ModelException ex = new ModelException("boom", leaf);
    assertTrue(ex.isRelatedTo(leaf));
    assertTrue(ex.isRelatedTo(mid));
    assertTrue(ex.isRelatedTo(root));
    assertFalse(ex.isRelatedTo(unrelated));
  }

  @Test
  public void testIsRelatedToWithoutElement() {
    ModelException ex = new ModelException("boom");
    assertFalse(ex.isRelatedTo(new TestElement("x", null, null)));
  }

  @Test
  public void testCollectionOfErrorsConstructor() {
    TestElement e1 = new TestElement("A", new SourceInfo("A.java", 1), null);
    TestElement e2 = new TestElement("B", new SourceInfo("B.java", 2), null);
    List<ModelError> errors = List.of(new ModelError(e1, "first"), new ModelError(e2, "second"));
    ModelException ex = new ModelException(errors);
    // first element with a non-null element is picked
    assertSame(ex.getElement(), e1);
    assertTrue(ex.getMessage().contains("first"));
    assertTrue(ex.getMessage().contains("second"));
  }


  // ---- ModelError ----------------------------------------------------------

  @Test
  public void testModelErrorAccessors() {
    TestElement element = new TestElement("Foo", new SourceInfo("Foo.java", 3), null);
    ModelError error = new ModelError(element, "broken");
    assertSame(error.getElement(), element);
    assertEquals(error.getMessage(), "broken");
    assertEquals(error.toString(), "Foo (Foo.java[3]): broken");
  }

  @Test
  public void testModelErrorToStringWithoutSourceInfo() {
    ModelError error = new ModelError(new TestElement("Foo", null, null), "broken");
    assertEquals(error.toString(), "Foo: broken");
  }

  @Test
  public void testModelErrorToStringMessageOnly() {
    ModelError error = new ModelError((ModelElement) null, "broken");
    assertEquals(error.toString(), "broken");
  }

  @Test
  public void testModelErrorFromException() {
    TestElement element = new TestElement("Foo", null, null);
    ModelException ex = new ModelException("boom", element);
    ModelError error = new ModelError(ex);
    assertSame(error.getElement(), element);
    assertEquals(error.getMessage(), "boom");
  }

  @Test
  public void testModelErrorEqualsHashCode() {
    TestElement element = new TestElement("Foo", null, null);
    ModelError a = new ModelError(element, "broken");
    ModelError b = new ModelError(element, "broken");
    ModelError differentMsg = new ModelError(element, "other");

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(a, a);
    assertFalse(a.equals(differentMsg));
    assertFalse(a.equals(null));
  }

  @Test
  public void testModelErrorStaticToString() {
    TestElement e1 = new TestElement("A", null, null);
    TestElement e2 = new TestElement("B", null, null);
    String s = ModelError.toString(List.of(new ModelError(e1, "first"), new ModelError(e2, "second")));
    assertEquals(s, "A: first\nB: second");
  }
}
