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

package org.tentackle.bind;

import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@link DefaultBindableElement}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultBindableElementTest {

  /** a mutable model with getter/setter and a field. */
  @SuppressWarnings("missing-explicit-ctor")
  public static class Model {

    public String publicField = "field-init";

    private String name;
    private int count;
    private List<String> tags;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }

    public List<String> getTags() {
      return tags;
    }

    public String readOnly() {
      return "ro";
    }
  }

  /** an immutable model providing a "with"-method. */
  public record Point(int x) {
    public Point withX(int newX) {
      return new Point(newX);
    }
  }


  private static Method method(Class<?> clazz, String name, Class<?>... args) {
    try {
      return clazz.getMethod(name, args);
    }
    catch (NoSuchMethodException ex) {
      throw new AssertionError(ex);
    }
  }

  private static Field field(Class<?> clazz, String name) {
    try {
      return clazz.getField(name);
    }
    catch (NoSuchFieldException ex) {
      throw new AssertionError(ex);
    }
  }


  @Test
  public void testCamelNameAndOrdinal() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    assertEquals(element.getCamelName(), "Name");
    assertEquals(element.getOrdinal(), 0);   // default

    element.setCamelName("Other");
    element.setOrdinal(7);
    assertEquals(element.getCamelName(), "Other");
    assertEquals(element.getOrdinal(), 7);
  }

  @Test
  public void testBindingOptions() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    assertNull(element.getBindingOptions());   // none by default

    element.setBindingOptions("UC,MAXCOLS=5");
    assertEquals(element.getBindingOptions(), "UC,MAXCOLS=5");

    // addBindingOptions appends and preserves order, de-duplicating
    element.addBindingOptions("UC,LC");
    assertEquals(element.getBindingOptions(), "UC,MAXCOLS=5,LC");

    // setBindingOptions replaces
    element.setBindingOptions("A,B");
    assertEquals(element.getBindingOptions(), "A,B");

    // blank/null clears
    element.setBindingOptions("   ");
    assertNull(element.getBindingOptions());

    element.addBindingOptions("X");
    element.setBindingOptions(null);
    assertNull(element.getBindingOptions());

    // blank additions are ignored
    element.addBindingOptions("  ");
    assertNull(element.getBindingOptions());
  }

  @Test
  public void testGetViaGetter() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setGetter(method(Model.class, "getName"));

    Model model = new Model();
    model.setName("hello");
    assertEquals(element.getObject(model), "hello");
    assertEquals(element.getType(), String.class);
  }

  @Test
  public void testGetViaField() {
    DefaultBindableElement element = new DefaultBindableElement("PublicField");
    element.setField(field(Model.class, "publicField"));

    Model model = new Model();
    assertEquals(element.getObject(model), "field-init");
    assertEquals(element.getType(), String.class);
  }

  @Test
  public void testGetterTakesPrecedenceOverField() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setGetter(method(Model.class, "getName"));
    element.setField(field(Model.class, "publicField"));   // must be ignored on get

    Model model = new Model();
    model.setName("from-getter");
    assertEquals(element.getObject(model), "from-getter");
  }

  @Test
  public void testSetViaSetter() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setGetter(method(Model.class, "getName"));   // needed to resolve the type
    element.setSetter(method(Model.class, "setName", String.class));

    Model model = new Model();
    assertNull(element.setObject(model, "world"));   // no from-method => null parent
    assertEquals(model.getName(), "world");
    assertFalse(element.isReadOnly());
  }

  @Test
  public void testSetViaField() {
    DefaultBindableElement element = new DefaultBindableElement("PublicField");
    element.setField(field(Model.class, "publicField"));

    Model model = new Model();
    assertNull(element.setObject(model, "changed"));
    assertEquals(model.publicField, "changed");
    assertFalse(element.isReadOnly());
  }

  @Test
  public void testSetterTakesPrecedenceOverField() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setSetter(method(Model.class, "setName", String.class));
    element.setField(field(Model.class, "publicField"));

    Model model = new Model();
    element.setObject(model, "via-setter");
    assertEquals(model.getName(), "via-setter");
    assertEquals(model.publicField, "field-init");   // field untouched
  }

  @Test
  public void testSetNullOnPrimitiveInt() {
    DefaultBindableElement element = new DefaultBindableElement("Count");
    element.setGetter(method(Model.class, "getCount"));
    element.setSetter(method(Model.class, "setCount", int.class));

    Model model = new Model();
    model.setCount(42);
    element.setObject(model, null);   // null on a primitive => 0
    assertEquals(model.getCount(), 0);
  }

  @Test
  public void testSetNullOnPrimitiveBoolean() {
    // synthetic element bound to a boolean field
    class BoolHolder {
      public boolean flag = true;
    }
    DefaultBindableElement element = new DefaultBindableElement("Flag");
    try {
      element.setField(BoolHolder.class.getField("flag"));
    }
    catch (NoSuchFieldException ex) {
      throw new AssertionError(ex);
    }

    BoolHolder holder = new BoolHolder();
    element.setObject(holder, null);   // null on boolean => false
    assertFalse(holder.flag);
  }

  @Test
  public void testFromMethodReturnsUpdatedParent() {
    DefaultBindableElement element = new DefaultBindableElement("X");
    element.setGetter(method(Point.class, "x"));
    element.setFrom(method(Point.class, "withX", int.class));

    Point point = new Point(1);
    Object updated = element.setObject(point, 5);
    assertEquals(updated, new Point(5));
    assertEquals(point.x(), 1);   // original is immutable / untouched
    assertFalse(element.isReadOnly());   // has a from-method
  }

  @Test
  public void testReadOnly() {
    DefaultBindableElement element = new DefaultBindableElement("ReadOnly");
    element.setGetter(method(Model.class, "readOnly"));
    assertTrue(element.isReadOnly());

    // setObject on a read-only element is a no-op that returns null
    Model model = new Model();
    assertNull(element.setObject(model, "ignored"));
  }

  @Test
  public void testGenericType() {
    DefaultBindableElement element = new DefaultBindableElement("Tags");
    element.setGetter(method(Model.class, "getTags"));

    assertEquals(element.getType(), List.class);
    assertTrue(element.getGenericType() instanceof ParameterizedType);
    ParameterizedType pt = (ParameterizedType) element.getGenericType();
    assertEquals(pt.getRawType(), List.class);
    assertEquals(pt.getActualTypeArguments()[0], String.class);
  }

  @Test
  public void testTypeIsCached() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setGetter(method(Model.class, "getName"));
    Class<?> first = element.getType();
    assertSame(element.getType(), first);
  }

  @Test
  public void testTypeWithoutGetterOrFieldThrows() {
    DefaultBindableElement element = new DefaultBindableElement("Nothing");
    BindingException ex = expectThrows(BindingException.class, element::getType);
    assertTrue(ex.getMessage().contains("Nothing"));
  }

  @Test
  public void testGenericTypeWithoutGetterOrFieldThrows() {
    DefaultBindableElement element = new DefaultBindableElement("Nothing");
    expectThrows(BindingException.class, element::getGenericType);
  }

  @Test
  public void testGetOnWrongParentThrowsBindingException() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setGetter(method(Model.class, "getName"));
    // passing an unrelated object => IllegalArgumentException wrapped into BindingException
    BindingException ex = expectThrows(BindingException.class, () -> element.getObject("not a model"));
    assertTrue(ex.getMessage().contains("cannot get Name"));
  }

  @Test
  public void testSetOnWrongParentThrowsBindingException() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setGetter(method(Model.class, "getName"));   // needed to resolve the type
    element.setSetter(method(Model.class, "setName", String.class));
    BindingException ex = expectThrows(BindingException.class, () -> element.setObject("not a model", "x"));
    assertTrue(ex.getMessage().contains("cannot set Name"));
  }
}
