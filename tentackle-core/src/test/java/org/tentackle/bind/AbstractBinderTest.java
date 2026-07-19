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

import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationScopeFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link AbstractBinder} bookkeeping (instance numbers, properties, listeners, scope).
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractBinderTest {

  /** minimal concrete binder that only exercises the {@link AbstractBinder} logic. */
  @SuppressWarnings("missing-explicit-ctor")
  public static class TestBinder extends AbstractBinder {
    @Override
    public void assertAllBound() {
    }

    @Override
    public int bind() {
      return 0;
    }

    @Override
    public int bindAllInherited() {
      return 0;
    }

    @Override
    public Binding getBinding(String bindingPath) {
      return null;
    }

    @Override
    public void addBinding(Binding binding) {
    }

    @Override
    public Collection<? extends Binding> getBindings() {
      return List.of();
    }

    @Override
    public Binding removeBinding(String bindingPath) {
      return null;
    }

    @Override
    public void unbind() {
    }

    @Override
    protected int doBind(BindingMember[] parents, String parentMemberPath, Class<?> parentClass, boolean declaredOnly) {
      return 0;
    }
  }

  /** a second concrete type to prove equals discriminates by class. */
  @SuppressWarnings("missing-explicit-ctor")
  public static class OtherBinder extends TestBinder {
  }


  @Test
  public void testInstanceNumbersAreUniqueAndIncreasing() {
    TestBinder a = new TestBinder();
    TestBinder b = new TestBinder();
    assertTrue(b.getInstanceNumber() > a.getInstanceNumber());
  }

  @Test
  public void testCompareTo() {
    TestBinder a = new TestBinder();
    TestBinder b = new TestBinder();
    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(a) > 0);
    assertEquals(a.compareTo(a), 0);
  }

  @Test
  public void testEqualsAndHashCode() {
    TestBinder a = new TestBinder();
    TestBinder b = new TestBinder();

    assertEquals(a, a);
    assertNotEquals(a, b);
    assertNotEquals(a, null);
    assertNotEquals(a, "not a binder");
    assertEquals(a.hashCode(), Long.hashCode(a.getInstanceNumber()));
  }

  @Test
  public void testEqualsDiscriminatesByClass() {
    TestBinder a = new TestBinder();
    OtherBinder b = new OtherBinder();
    assertNotEquals(a, b);
  }

  @Test
  public void testBindingPropertyByKey() {
    TestBinder binder = new TestBinder();
    assertNull(binder.getBindingProperty("k"));
    assertNull(binder.putBindingProperty("k", "v"));       // no previous
    assertEquals(binder.getBindingProperty("k"), "v");
    assertEquals(binder.putBindingProperty("k", "v2"), "v");   // returns previous
    assertEquals(binder.getBindingProperty("k"), "v2");
  }

  @Test
  public void testBindingPropertyByClass() {
    TestBinder binder = new TestBinder();
    binder.putBindingProperty(String.class, "hello");
    String value = binder.getBindingProperty(String.class);
    assertEquals(value, "hello");
    assertNull(binder.getBindingProperty(Integer.class));
  }

  @Test
  public void testPutBindingProperties() {
    TestBinder binder = new TestBinder();
    assertTrue(binder.putBindingProperties(Map.of("a", 1, "b", 2)));
    assertEquals(binder.getBindingProperty("a"), 1);
    assertEquals(binder.getBindingProperty("b"), 2);

    // setting identical values does not count as a change
    assertFalse(binder.putBindingProperties(Map.of("a", 1, "b", 2)));

    // a differing value counts as a change
    assertTrue(binder.putBindingProperties(Map.of("a", 99)));
  }

  @Test
  public void testToViewListeners() {
    TestBinder binder = new TestBinder();
    assertNotNull(binder.getToViewListeners());
    assertTrue(binder.getToViewListeners().isEmpty());

    ToViewListener l = _ -> { };
    binder.addToViewListener(l);
    assertEquals(binder.getToViewListeners().size(), 1);
    binder.removeToViewListener(l);
    assertTrue(binder.getToViewListeners().isEmpty());
  }

  @Test
  public void testToModelListeners() {
    TestBinder binder = new TestBinder();
    ToModelListener l = _ -> { };
    binder.addToModelListener(l);
    assertEquals(binder.getToModelListeners().size(), 1);
    binder.removeToModelListener(l);
    assertTrue(binder.getToModelListeners().isEmpty());
  }

  @Test
  public void testValidationListeners() {
    TestBinder binder = new TestBinder();
    ValidationListener l = _ -> { };
    binder.addValidationListener(l);
    assertEquals(binder.getValidationListeners().size(), 1);
    binder.removeValidationListener(l);
    assertTrue(binder.getValidationListeners().isEmpty());
  }

  @Test
  public void testValidationScopeDefaultsToInteractive() {
    TestBinder binder = new TestBinder();
    ValidationScope interactive = ValidationScopeFactory.getInstance().getInteractiveScope();
    assertSame(binder.getValidationScope(), interactive);
  }

  @Test
  public void testSetValidationScope() {
    TestBinder binder = new TestBinder();
    ValidationScope all = ValidationScopeFactory.getInstance().getAllScope();
    binder.setValidationScope(all);
    assertSame(binder.getValidationScope(), all);
  }

  @Test
  public void testFireWithoutListenersIsNoOp() throws BindingVetoException {
    TestBinder binder = new TestBinder();
    // no listeners registered => nothing happens, no NPE
    binder.fireToView(null, null, null);
    binder.fireToModel(null, null, null);
    binder.fireValidated(null, List.of());
  }
}
