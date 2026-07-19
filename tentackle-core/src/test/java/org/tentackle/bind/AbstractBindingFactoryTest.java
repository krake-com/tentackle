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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link AbstractBindingFactory}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractBindingFactoryTest {

  /** a custom bindable element to verify the class mapping. */
  @SuppressWarnings("missing-explicit-ctor")
  public static class CustomElement extends DefaultBindableElement {
    public CustomElement(String camelName) {
      super(camelName);
    }
  }

  public static class ModelBase {
  }

  public static class ModelDerived extends ModelBase {
  }

  public static class Unrelated {
  }


  @Test
  public void testCreatesDefaultElementByDefault() {
    AbstractBindingFactory factory = new AbstractBindingFactory();
    BindableElement element = factory.createBindableElement(String.class, "Name");
    assertEquals(element.getClass(), DefaultBindableElement.class);
    assertEquals(element.getCamelName(), "Name");
  }

  @Test
  public void testBindableCacheNotNullAndStable() {
    AbstractBindingFactory factory = new AbstractBindingFactory();
    BindableCache cache = factory.getBindableCache();
    assertNotNull(cache);
    assertSame(factory.getBindableCache(), cache);
  }

  @Test
  public void testPutAndGetBindableElementClass() {
    AbstractBindingFactory factory = new AbstractBindingFactory();
    // NOTE: querying before putting would cache a negative result, so register the mapping first
    assertNull(factory.putBindableElementClass(ModelBase.class, CustomElement.class));   // no previous
    assertEquals(factory.getBindableElementClass(ModelBase.class), CustomElement.class);
  }

  @Test
  public void testNegativeLookupIsCached() {
    AbstractBindingFactory factory = new AbstractBindingFactory();
    // first (negative) lookup is remembered: a mapping added afterward is not seen
    assertNull(factory.getBindableElementClass(ModelBase.class));
    factory.putBindableElementClass(ModelBase.class, CustomElement.class);
    assertNull(factory.getBindableElementClass(ModelBase.class));
  }

  @Test
  public void testElementClassInheritedFromSuperclass() {
    AbstractBindingFactory factory = new AbstractBindingFactory();
    factory.putBindableElementClass(ModelBase.class, CustomElement.class);

    // a derived model class inherits the mapping of its superclass
    assertEquals(factory.getBindableElementClass(ModelDerived.class), CustomElement.class);
  }

  @Test
  public void testUnmappedClassReturnsNull() {
    AbstractBindingFactory factory = new AbstractBindingFactory();
    factory.putBindableElementClass(ModelBase.class, CustomElement.class);
    assertNull(factory.getBindableElementClass(Unrelated.class));
  }

  @Test
  public void testCreateBindableElementUsesMappedClass() {
    AbstractBindingFactory factory = new AbstractBindingFactory();
    factory.putBindableElementClass(ModelBase.class, CustomElement.class);

    BindableElement element = factory.createBindableElement(ModelDerived.class, "Foo");
    assertEquals(element.getClass(), CustomElement.class);
    assertEquals(element.getCamelName(), "Foo");
  }

  @Test
  public void testCreateBindingMember() {
    AbstractBindingFactory factory = new AbstractBindingFactory();
    BindableElement element = factory.createBindableElement(String.class, "Name");
    BindingMember member = factory.createBindingMember(ModelBase.class, null, "name", "root.name", element);

    assertTrue(member instanceof DefaultBindingMember);
    assertEquals(member.getParentClass(), ModelBase.class);
    assertNull(member.getParentMember());
    assertEquals(member.getMemberName(), "name");
    assertEquals(member.getMemberPath(), "root.name");
  }
}
