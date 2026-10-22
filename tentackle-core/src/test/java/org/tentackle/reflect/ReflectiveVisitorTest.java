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

package org.tentackle.reflect;

import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Tests for {@link ReflectiveVisitor} double dispatch.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ReflectiveVisitorTest {

  private static class Shape { }
  private static class Circle extends Shape { }
  private static class Square extends Shape { }

  /**
   * Visitor that records the most specific method invoked.
   */
  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  private static class RecordingVisitor extends ReflectiveVisitor {
    String visited;
    public void visit(Shape shape) { visited = "shape"; }
    public void visit(Circle circle) { visited = "circle"; }
  }

  private static Class<?>[] shapeSlot() {
    return new Class<?>[]{Shape.class};
  }

  @Test
  public void testMostSpecificMethod() throws NoSuchMethodException {
    RecordingVisitor visitor = new RecordingVisitor();
    Circle circle = new Circle();
    visitor.visit(circle, shapeSlot(), circle);
    assertEquals(visitor.visited, "circle");
  }

  /**
   * Visitor class used only by {@link #testCacheDoesNotMixUpClasses()}, since the method cache is static.
   */
  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  private static class CachingVisitor extends ReflectiveVisitor {
    String visited;
    public void visit(Shape shape) { visited = "shape"; }
    public void visit(Circle circle) { visited = "circle"; }
  }

  /**
   * The first element of the types is a placeholder for the visited class.
   * The cached method of one class must not be returned for another class.
   */
  @Test
  public void testCacheDoesNotMixUpClasses() throws NoSuchMethodException {
    CachingVisitor visitor = new CachingVisitor();
    Shape shape = new Shape();
    Circle circle = new Circle();
    visitor.visit(shape, shapeSlot(), shape);
    assertEquals(visitor.visited, "shape");
    visitor.visit(circle, shapeSlot(), circle);
    assertEquals(visitor.visited, "circle");
    visitor.visit(circle, shapeSlot(), circle);   // now from the cache
    assertEquals(visitor.visited, "circle");
    visitor.visit(shape, shapeSlot(), shape);
    assertEquals(visitor.visited, "shape");
  }

  @Test
  public void testFallbackToSuperclass() throws NoSuchMethodException {
    RecordingVisitor visitor = new RecordingVisitor();
    Square square = new Square();
    // no visit(Square) exists -> the superclass method visit(Shape) is used
    visitor.visit(square, shapeSlot(), square);
    assertEquals(visitor.visited, "shape");
  }

  @Test
  public void testNoMatchingMethodThrows() {
    RecordingVisitor visitor = new RecordingVisitor();
    // a String has no visit method and none of its superclasses either
    assertThrows(NoSuchMethodException.class,
        () -> visitor.visit("no visitor for me", new Class<?>[]{String.class}, "arg"));
  }


  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  private static class RuntimeFailingVisitor extends ReflectiveVisitor {
    public void visit(Shape shape) {
      throw new IllegalStateException("boom");
    }
  }

  @Test
  public void testRuntimeExceptionPropagatesUnwrapped() {
    RuntimeFailingVisitor visitor = new RuntimeFailingVisitor();
    Shape shape = new Shape();
    // a RuntimeException thrown by the visit method must pass through directly
    assertThrows(IllegalStateException.class, () -> visitor.visit(shape, shapeSlot(), shape));
  }


  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  private static class CheckedFailingVisitor extends ReflectiveVisitor {
    public void visit(Shape shape) throws Exception {
      throw new Exception("checked");
    }
  }

  @Test
  public void testCheckedExceptionWrapped() {
    CheckedFailingVisitor visitor = new CheckedFailingVisitor();
    Shape shape = new Shape();
    // a checked exception is wrapped into a TentackleRuntimeException
    assertThrows(TentackleRuntimeException.class, () -> visitor.visit(shape, shapeSlot(), shape));
  }
}
